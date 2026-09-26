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
import java.util.List;

import androidx.annotation.Nullable;
import m.co.rh.id.a_flash_deck.base.dao.CardReviewStateDao;
import m.co.rh.id.a_flash_deck.base.dao.StudyDao;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;
import m.co.rh.id.a_flash_deck.base.entity.ReviewLog;
import m.co.rh.id.a_flash_deck.base.model.ReviewScheduler;
import m.co.rh.id.a_flash_deck.base.room.AppDatabase;

/**
 * Repository for study-domain persistence: grade writes, suspend state,
 * due-card selection and count, review-state reads
 */
public class StudyRepository {

    private AppDatabase mAppDatabase;
    private StudyDao mStudyDao;
    private CardReviewStateDao mReviewStateDao;

    public StudyRepository(AppDatabase appDatabase, StudyDao studyDao,
                           CardReviewStateDao reviewStateDao) {
        mAppDatabase = appDatabase;
        mStudyDao = studyDao;
        mReviewStateDao = reviewStateDao;
    }

    /**
     * Applies a study grade to the card's review state and schedules the next
     * due date time. Review-state rows are created lazily (on first grade or
     * first suspend), so grading a never-studied card creates its row here.
     * Also appends a ReviewLog entry so the statistics page can report daily
     * review counts, streak and retention.
     * The find-or-create-and-schedule runs in a transaction so a concurrent
     * suspend cannot interleave between the read and the upsert.
     */
    public void applyGrade(long cardId, long deckId, int grade, Date now) {
        mAppDatabase.runInTransaction(() -> {
            CardReviewState cardReviewState = mReviewStateDao.findByCardId(cardId);
            if (cardReviewState == null) {
                cardReviewState = ReviewScheduler.newCardReviewState(cardId);
            }
            ReviewScheduler.schedule(cardReviewState, grade, now);
            mReviewStateDao.insertReviewState(cardReviewState);
            ReviewLog reviewLog = new ReviewLog();
            reviewLog.cardId = cardId;
            reviewLog.deckId = deckId;
            reviewLog.grade = grade;
            reviewLog.createdDateTime = new Date(now.getTime());
            mAppDatabase.reviewLogDao().insertReviewLog(reviewLog);
        });
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
