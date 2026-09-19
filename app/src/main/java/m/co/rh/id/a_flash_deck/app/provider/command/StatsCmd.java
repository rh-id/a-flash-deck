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

package m.co.rh.id.a_flash_deck.app.provider.command;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.base.dao.CardDao;
import m.co.rh.id.a_flash_deck.base.dao.CardReviewStateDao;
import m.co.rh.id.a_flash_deck.base.dao.DeckDao;
import m.co.rh.id.a_flash_deck.base.dao.ReviewLogDao;
import m.co.rh.id.a_flash_deck.base.dao.StudyDao;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.entity.ReviewLog;
import m.co.rh.id.a_flash_deck.base.model.DeckDueCount;
import m.co.rh.id.a_flash_deck.base.model.ReviewStats;
import m.co.rh.id.a_flash_deck.base.model.ReviewStatsCalculator;
import m.co.rh.id.aprovider.Provider;

/**
 * Command that assembles the statistics page data: review history, due
 * forecast and per-deck breakdown
 */
public class StatsCmd {
    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

    private ExecutorService mExecutorService;
    private ReviewLogDao mReviewLogDao;
    private CardReviewStateDao mCardReviewStateDao;
    private StudyDao mStudyDao;
    private DeckDao mDeckDao;
    private CardDao mCardDao;

    public StatsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mReviewLogDao = provider.get(ReviewLogDao.class);
        mCardReviewStateDao = provider.get(CardReviewStateDao.class);
        mStudyDao = provider.get(StudyDao.class);
        mDeckDao = provider.get(DeckDao.class);
        mCardDao = provider.get(CardDao.class);
    }

    /**
     * @return the current statistics snapshot
     */
    public Single<ReviewStats> getReviewStats() {
        return Single.fromCallable(this::computeReviewStats)
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    private ReviewStats computeReviewStats() {
        Date now = new Date();
        long windowStartMs = now.getTime()
                - (long) ReviewStatsCalculator.DAILY_REVIEW_DAYS * ONE_DAY_MS;
        List<ReviewLog> reviewLogs = mReviewLogDao.findReviewLogsSince(windowStartMs);
        List<Date> dueDateTimes = mCardReviewStateDao.findDueDateTimes();
        int newCardCount = mStudyDao.countNewCards();
        List<DeckDueCount> deckDueCounts =
                mStudyDao.countDueAndNewCardsByDeckId(now.getTime());
        enrichWithDeckInfo(deckDueCounts);
        int totalReviews = mReviewLogDao.countAll();
        return ReviewStatsCalculator.compute(reviewLogs, dueDateTimes,
                newCardCount, deckDueCounts, totalReviews, now);
    }

    /**
     * Fills the deck name and total card count of each per-deck row;
     * decks deleted between the two queries are skipped
     */
    private void enrichWithDeckInfo(List<DeckDueCount> deckDueCounts) {
        if (deckDueCounts == null || deckDueCounts.isEmpty()) {
            return;
        }
        Map<Long, Deck> deckById = new HashMap<>();
        List<Deck> decks = mDeckDao.getAllDecks();
        if (decks != null) {
            for (Deck deck : decks) {
                deckById.put(deck.id, deck);
            }
        }
        for (DeckDueCount deckDueCount : deckDueCounts) {
            Deck deck = deckById.get(deckDueCount.deckId);
            if (deck != null) {
                deckDueCount.deckName = deck.name;
                deckDueCount.totalCards = mCardDao.countCardByDeckId(deck.id);
            }
        }
    }
}
