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
     * Review speed per day of the last 30 days, oldest day first, today
     * last: mean time in ms from card shown to answer revealed of the
     * day's valid timing samples (see
     * {@link ReviewStatsCalculator#MAX_VALID_TIME_BEFORE_REVEAL_MS}),
     * 0 when a day has no valid sample. Never uses timeAfterRevealMs
     */
    public int[] dailyAvgSpeedMs;

    /**
     * Median over all valid timing samples in the last 30 days; with an
     * even sample count the median is defined as the lower middle sample
     * of the samples sorted ascending; 0 without a valid sample
     */
    public long medianSpeedMs;

    /**
     * Mean over all valid timing samples in the last 30 days,
     * truncated to whole ms; 0 without a valid sample
     */
    public long avgSpeedMs;

    /**
     * Count of valid timing samples in the last 30 days
     * (timeBeforeRevealMs greater than 0 and at most 10 minutes);
     * 0 means no sample at all, e.g. for upgrades from app versions that
     * did not record timings yet
     */
    public int speedSampleCount;

    /**
     * True retention of the last 30 days: window reviews bucketed by the
     * whole-day gap to the previous review of the same card
     */
    public TrueRetention trueRetention;

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
