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

package m.co.rh.id.a_flash_deck.base.model;

import java.io.Serializable;
import java.util.List;

/**
 * Immutable snapshot of the review statistics shown on the statistics page.
 * All values are computed by {@link ReviewStatsCalculator}.
 */
public class ReviewStats implements Serializable {

    /**
     * Review count per day of the last 30 days, oldest day first, today last
     */
    public int[] dailyReviewCounts;

    /**
     * Review count of today
     */
    public int todayReviewCount;

    /**
     * Consecutive days with at least one review, ending today
     * (or ending yesterday when today has no review yet)
     */
    public int streakDays;

    /**
     * Percentage of reviews in the last 30 days that were not graded Again
     */
    public double retentionPercent;

    /**
     * Total review count in the last 30 days window (retention sample size)
     */
    public int retentionTotal;

    /**
     * Today's review count per grade, indexed by grade
     * (ReviewScheduler.GRADE_AGAIN .. ReviewScheduler.GRADE_EASY)
     */
    public int[] todayGradeCounts;

    /**
     * Due card count per day of the next 14 days, today first
     */
    public int[] dailyDueCounts;

    /**
     * Count of scheduled cards due later than the 14 days forecast window
     */
    public int dueLaterCount;

    /**
     * Count of new/never studied cards across all decks
     */
    public int newCardCount;

    /**
     * Total review count ever recorded
     */
    public int totalReviews;

    /**
     * Per-deck due/new counts, enriched with deck name and total card count
     */
    public List<DeckDueCount> deckDueCounts;
}
