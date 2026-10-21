/*
 *     Copyright (C) 2021-present Ruby Hartono
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package m.co.rh.id.a_flash_deck.base.repository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.base.component.AppSharedPreferences;
import m.co.rh.id.a_flash_deck.base.dao.CardReviewStateDao;
import m.co.rh.id.a_flash_deck.base.dao.StudyDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;
import m.co.rh.id.a_flash_deck.base.entity.ReviewLog;
import m.co.rh.id.a_flash_deck.base.model.FsrsScheduler;
import m.co.rh.id.a_flash_deck.base.model.FsrsParams;
import m.co.rh.id.a_flash_deck.base.model.ReviewScheduler;
import m.co.rh.id.a_flash_deck.base.model.SchedulerMode;
import m.co.rh.id.a_flash_deck.base.room.AppDatabase;

/**
 * Repository for study-domain persistence: grade writes, suspend state,
 * due-card selection and count, review-state reads
 */
public class StudyRepository {

    private AppDatabase mAppDatabase;
    private StudyDao mStudyDao;
    private CardReviewStateDao mReviewStateDao;
    private AppSharedPreferences mAppSharedPreferences;

    public StudyRepository(AppDatabase appDatabase, StudyDao studyDao,
                           CardReviewStateDao reviewStateDao,
                           AppSharedPreferences appSharedPreferences) {
        mAppDatabase = appDatabase;
        mStudyDao = studyDao;
        mReviewStateDao = reviewStateDao;
        mAppSharedPreferences = appSharedPreferences;
    }

    /**
     * Applies a study grade to the card's review state and schedules the next
     * due date time. Review-state rows are created lazily (on first grade or
     * first suspend), so grading a never-studied card creates its row here.
     * <p>
     * Both schedulers are advanced on every grade (dual-write): FSRS-6 first
     * (it captures the previous last_review_date_time for the elapsed days,
     * updates stability/difficulty and then stamps last_review_date_time),
     * then SM-2-lite (it overwrites due_date_time with the SM-2 due and
     * updates interval_days/ease_factor/repetitions/lapses; its new-card
     * check is unaffected because FSRS only stamps last_review_date_time).
     * Afterwards, when FSRS is the active mode, due_date_time is overridden
     * with the FSRS due date; in SM-2 mode the SM-2 due date stays.
     * Also appends a ReviewLog entry (including the answer timing split and
     * the reversed flag) so the statistics page can report daily
     * review counts, streak and retention.
     * The find-or-create-and-schedule runs in a transaction so a concurrent
     * suspend cannot interleave between the read and the upsert.
     */
    public void applyGrade(long cardId, long deckId, int grade, Date now,
                           long timeBeforeRevealMs, long timeAfterRevealMs,
                           boolean reversed) {
        mAppDatabase.runInTransaction(() -> {
            CardReviewState cardReviewState = mReviewStateDao.findByCardId(cardId);
            if (cardReviewState == null) {
                cardReviewState = ReviewScheduler.newCardReviewState(cardId);
            }
            int schedulerMode = mAppSharedPreferences.getSchedulerMode();
            double desiredRetention = mAppSharedPreferences.getDesiredRetention();
            FsrsScheduler.schedule(cardReviewState, grade, now, desiredRetention);
            ReviewScheduler.schedule(cardReviewState, grade, now);
            if (schedulerMode == SchedulerMode.FSRS) {
                cardReviewState.dueDateTime = FsrsScheduler.computeDueDate(
                        cardReviewState, grade, now, desiredRetention);
            }
            mReviewStateDao.insertReviewState(cardReviewState);
            ReviewLog reviewLog = new ReviewLog();
            reviewLog.cardId = cardId;
            reviewLog.deckId = deckId;
            reviewLog.grade = grade;
            reviewLog.createdDateTime = new Date(now.getTime());
            reviewLog.timeBeforeRevealMs = timeBeforeRevealMs;
            reviewLog.timeAfterRevealMs = timeAfterRevealMs;
            reviewLog.reversed = reversed;
            mAppDatabase.reviewLogDao().insertReviewLog(reviewLog);
        });
    }

    /**
     * One-shot background recompute of the due date time of every reviewed
     * card, to run after the scheduler mode or the FSRS desired retention
     * changed. The stored scheduler states are not touched (they are kept
     * up to date by the dual-write in {@link #applyGrade}):
     * SM-2 mode: due = last_review + min(interval_days, 365) days.
     * FSRS mode: due = last_review + FsrsScheduler.intervalDays(
     * stability, desiredRetention) days; rows without FSRS state yet
     * (stability &lt;= 0) use max(interval_days, 0.5) as stability for this
     * computation only (the real seeding happens on the next grade); rows
     * whose latest review log grade is AGAIN stay due in 10 minutes.
     * Serialized against concurrent grades because both run inside Room
     * transactions (single write connection).
     *
     * @return a background Single that emits the number of recomputed rows
     */
    public Single<Integer> rescheduleAllForSchedulerChange() {
        return Single.fromCallable(() -> {
            int schedulerMode = mAppSharedPreferences.getSchedulerMode();
            double desiredRetention = mAppSharedPreferences.getDesiredRetention();
            return mAppDatabase.runInTransaction(() -> {
                List<CardReviewState> reviewStates =
                        mReviewStateDao.findReviewedStates();
                if (reviewStates.isEmpty()) {
                    return 0;
                }
                Map<Long, ReviewLog> latestLogByCardId = new HashMap<>();
                for (ReviewLog reviewLog
                        : mAppDatabase.reviewLogDao().findLatestReviewLogs()) {
                    latestLogByCardId.put(reviewLog.cardId, reviewLog);
                }
                for (CardReviewState reviewState : reviewStates) {
                    reviewState.dueDateTime = computeDueDateForSchedulerChange(
                            reviewState, latestLogByCardId.get(reviewState.cardId),
                            schedulerMode, desiredRetention);
                    mReviewStateDao.insertReviewState(reviewState);
                }
                return reviewStates.size();
            });
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Computes the recomputed due date time of one card after a scheduler
     * setting change, based on its last_review_date_time. Does not mutate
     * the given state beyond the returned date.
     */
    private Date computeDueDateForSchedulerChange(CardReviewState reviewState,
                                                  @Nullable ReviewLog latestReviewLog,
                                                  int schedulerMode,
                                                  double desiredRetention) {
        Date lastReviewDateTime = reviewState.lastReviewDateTime;
        if (schedulerMode == SchedulerMode.FSRS) {
            // FSRS: schedule from the last review with the effective
            // stability (rows without FSRS state yet fall back to their
            // SM-2 interval, seeded for this computation only)
            CardReviewState dueComputationState = new CardReviewState();
            dueComputationState.intervalDays = reviewState.intervalDays;
            dueComputationState.stability = reviewState.stability > 0
                    ? reviewState.stability
                    : Math.max(reviewState.intervalDays,
                    FsrsParams.SEED_STABILITY_FLOOR);
            int latestGrade = latestReviewLog != null
                    ? latestReviewLog.grade
                    : ReviewScheduler.GRADE_GOOD;
            return FsrsScheduler.computeDueDate(dueComputationState, latestGrade,
                    lastReviewDateTime, desiredRetention);
        }
        // SM-2: same semantics as ReviewScheduler.dueFromInterval
        double cappedIntervalDays = Math.min(reviewState.intervalDays,
                ReviewScheduler.INTERVAL_CAP_DAYS);
        return new Date(lastReviewDateTime.getTime()
                + Math.round(cappedIntervalDays * ReviewScheduler.ONE_DAY_MS));
    }

    /**
     * Sets the suspended flag of the card's review state.
     * Review-state rows are created lazily (on first grade or first suspend),
     * so suspending a never-studied card creates its row here with
     * dueDateTime still null until the first grade; the study queries treat a
     * null dueDateTime as new/due again after unsuspend.
     * The find-or-create runs in a transaction so a concurrent grade cannot
     * interleave between the read and the upsert.
     */
    public void suspendCard(long cardId, boolean suspend) {
        mAppDatabase.runInTransaction(() -> {
            CardReviewState reviewState = mReviewStateDao.findByCardId(cardId);
            if (reviewState == null) {
                reviewState = ReviewScheduler.newCardReviewState(cardId);
            }
            reviewState.suspended = suspend;
            mReviewStateDao.insertReviewState(reviewState);
        });
    }

    /**
     * @return total count of due or new cards across all decks
     */
    public int getDueCardCount() {
        return mStudyDao.countDueAndNewCards(System.currentTimeMillis());
    }

    /**
     * @return all due or new cards across all decks, ordered by ordinal then id
     */
    public List<Card> findDueCards(long now) {
        return mStudyDao.findDueAndNewCards(now);
    }

    /**
     * @return count of due or new cards of the given deck
     */
    public int getDueCardCount(long deckId) {
        return mStudyDao.countDueAndNewCardsBySingleDeckId(deckId, System.currentTimeMillis());
    }

    /**
     * @return all due or new cards of the given decks
     */
    public List<Card> findDueCardsByDeckIds(List<Long> deckIds) {
        return mStudyDao.findDueAndNewCardsByDeckIds(deckIds, System.currentTimeMillis());
    }

    /**
     * @return the review state of a card, or null when the card is
     * new/never studied (review-state rows are created lazily on
     * first grade or first suspend)
     */
    @Nullable
    public CardReviewState getReviewStateByCardId(long cardId) {
        return mReviewStateDao.findByCardId(cardId);
    }
}
