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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import m.co.rh.id.a_flash_deck.base.entity.ReviewLog;

/**
 * Static utility that computes {@link ReviewStats} from the raw study data.
 * Day bucketing is done with {@link Calendar} only (no java.time), each
 * timestamp is normalized to its local calendar day before comparison so the
 * result never depends on the time of day.
 */
public final class ReviewStatsCalculator {

    /**
     * Number of days of the daily review count window, oldest first, today last
     */
    public static final int DAILY_REVIEW_DAYS = 30;

    /**
     * Number of days of the due forecast window, today first
     */
    public static final int DUE_FORECAST_DAYS = 14;

    /**
     * Maximum timeBeforeRevealMs of a valid answer-speed timing sample
     * (10 minutes). Samples with 0 or negative time (app versions before
     * the feature existed, backwards clock change) and larger samples
     * (session stalled in between) are excluded from every speed statistic
     */
    public static final long MAX_VALID_TIME_BEFORE_REVEAL_MS = 600_000L;

    /**
     * Number of true-retention buckets, indexed by whole-day gap to the
     * previous review of the same card: 0 = less than 1 day, 1 = 1 to 7
     * days, 2 = 8 to 30 days, 3 = more than 30 days
     */
    public static final int TRUE_RETENTION_BUCKET_COUNT = 4;

    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

    private ReviewStatsCalculator() {
    }

    /**
     * @return epoch ms of local midnight of the first day of the review
     * statistics window (the day 29 days before the given reference date),
     * the same calendar-day boundary the day bucketing of
     * {@link #compute} compares timestamps with. A rolling cutoff like
     * "now minus 30x24h" can sit up to a day late, which would silently
     * drop prior reviews of that band from a "reviews before the window"
     * query; callers of such queries should use this cutoff instead
     */
    public static long reviewWindowStartMs(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -DAILY_REVIEW_DAYS + 1);
        return calendar.getTimeInMillis();
    }

    /**
     * Computes the review statistics from the given raw data.
     * Null lists are tolerated and treated as empty.
     *
     * @param reviewLogs             review history entries (any range, only the
     *                               last 30 days are used)
     * @param lastReviewBeforeWindow latest review time of every card reviewed
     *                               before the calendar-day start of the window
     *                               ({@link #reviewWindowStartMs}; boundary
     *                               data for the true-retention gap of the
     *                               first in-window review of a card)
     * @param dueDateTimes           due date time of every not suspended card
     *                               that has one scheduled
     * @param newCardCount           count of new/never studied cards across
     *                               all decks
     * @param deckDueCounts          per-deck due/new counts
     * @param totalReviews           total review count ever recorded
     * @param now                    reference "today" used for all day bucketing
     * @return the computed statistics snapshot
     */
    public static ReviewStats compute(List<ReviewLog> reviewLogs,
                                      List<CardLastReview> lastReviewBeforeWindow,
                                      List<Date> dueDateTimes,
                                      int newCardCount, List<DeckDueCount> deckDueCounts,
                                      int totalReviews, Date now) {
        List<ReviewLog> logs = reviewLogs == null
                ? Collections.emptyList() : reviewLogs;
        List<CardLastReview> lastReviews = lastReviewBeforeWindow == null
                ? Collections.emptyList() : lastReviewBeforeWindow;
        List<Date> dueDates = dueDateTimes == null
                ? Collections.emptyList() : dueDateTimes;
        List<DeckDueCount> deckDueCountList = deckDueCounts == null
                ? Collections.emptyList() : deckDueCounts;

        ReviewStats stats = new ReviewStats();
        stats.dailyReviewCounts = new int[DAILY_REVIEW_DAYS];
        stats.todayGradeCounts = new int[ReviewScheduler.GRADE_EASY + 1];
        stats.dailyDueCounts = new int[DUE_FORECAST_DAYS];
        stats.dailyAvgSpeedMs = new int[DAILY_REVIEW_DAYS];
        stats.newCardCount = newCardCount;
        stats.totalReviews = totalReviews;
        stats.deckDueCounts = new ArrayList<>(deckDueCountList);

        int todayDayNumber = dayNumber(now);
        Set<Integer> reviewDaySet = new HashSet<>();
        int retentionNonAgainCount = 0;
        int retentionTotalCount = 0;
        // answer-speed accumulators indexed like dailyReviewCounts
        long[] daySpeedSumMs = new long[DAILY_REVIEW_DAYS];
        int[] daySpeedSamples = new int[DAILY_REVIEW_DAYS];
        List<Long> validSpeedsMs = new ArrayList<>();
        // all in-window logs, also fed to the true-retention pass below
        List<ReviewLog> windowLogs = new ArrayList<>();
        for (ReviewLog reviewLog : logs) {
            if (reviewLog == null || reviewLog.createdDateTime == null) {
                continue;
            }
            // day difference to today: 0 = today, -1 = yesterday, ...
            int dayDiff = dayNumber(reviewLog.createdDateTime) - todayDayNumber;
            if (dayDiff < -DAILY_REVIEW_DAYS + 1 || dayDiff > 0) {
                continue;
            }
            reviewDaySet.add(dayDiff);
            int dayIndex = DAILY_REVIEW_DAYS - 1 + dayDiff;
            stats.dailyReviewCounts[dayIndex]++;
            retentionTotalCount++;
            if (dayDiff == 0 && reviewLog.grade >= 0
                    && reviewLog.grade < stats.todayGradeCounts.length) {
                stats.todayGradeCounts[reviewLog.grade]++;
            }
            if (reviewLog.grade != ReviewScheduler.GRADE_AGAIN) {
                retentionNonAgainCount++;
            }
            long speedMs = reviewLog.timeBeforeRevealMs;
            if (speedMs > 0 && speedMs <= MAX_VALID_TIME_BEFORE_REVEAL_MS) {
                daySpeedSumMs[dayIndex] += speedMs;
                daySpeedSamples[dayIndex]++;
                validSpeedsMs.add(speedMs);
            }
            windowLogs.add(reviewLog);
        }
        stats.todayReviewCount = stats.dailyReviewCounts[DAILY_REVIEW_DAYS - 1];
        stats.streakDays = computeStreak(reviewDaySet);

        stats.retentionTotal = retentionTotalCount;
        stats.retentionPercent = stats.retentionTotal == 0 ? 0.0
                : retentionNonAgainCount * 100.0 / stats.retentionTotal;

        computeSpeedSummary(stats, validSpeedsMs, daySpeedSumMs, daySpeedSamples);
        computeTrueRetention(stats, windowLogs, lastReviews);

        for (Date dueDateTime : dueDates) {
            if (dueDateTime == null) {
                continue;
            }
            int dayDiff = dayNumber(dueDateTime) - todayDayNumber;
            if (dayDiff >= 0 && dayDiff < DUE_FORECAST_DAYS) {
                stats.dailyDueCounts[dayDiff]++;
            } else if (dayDiff >= DUE_FORECAST_DAYS) {
                stats.dueLaterCount++;
            }
        }
        return stats;
    }

    /**
     * Fills the answer-speed summary of the given stats from the valid
     * timing samples collected in the window
     */
    private static void computeSpeedSummary(ReviewStats stats, List<Long> validSpeedsMs,
                                            long[] daySpeedSumMs, int[] daySpeedSamples) {
        stats.speedSampleCount = validSpeedsMs.size();
        if (stats.speedSampleCount > 0) {
            long speedSumMs = 0;
            for (long speedMs : validSpeedsMs) {
                speedSumMs += speedMs;
            }
            stats.avgSpeedMs = speedSumMs / stats.speedSampleCount;
            // median defined as the lower middle sample of the samples
            // sorted ascending
            Collections.sort(validSpeedsMs);
            stats.medianSpeedMs = validSpeedsMs.get((stats.speedSampleCount - 1) / 2);
        }
        for (int dayIndex = 0; dayIndex < DAILY_REVIEW_DAYS; dayIndex++) {
            stats.dailyAvgSpeedMs[dayIndex] = daySpeedSamples[dayIndex] == 0 ? 0
                    : (int) (daySpeedSumMs[dayIndex] / daySpeedSamples[dayIndex]);
        }
    }

    /**
     * Fills the true-retention buckets of the given stats: every window
     * review is bucketed by the whole-day gap to the previous review of
     * the same card, which is the latest earlier review in the window, or
     * the boundary value from the {@code lastReviews} list when the card
     * was not yet reviewed in the window, or none (first-ever review,
     * counted in {@code newCardCount})
     */
    private static void computeTrueRetention(ReviewStats stats, List<ReviewLog> windowLogs,
                                             List<CardLastReview> lastReviews) {
        stats.trueRetention = new TrueRetention();
        stats.trueRetention.bucketCorrectCounts = new int[TRUE_RETENTION_BUCKET_COUNT];
        stats.trueRetention.bucketTotalCounts = new int[TRUE_RETENTION_BUCKET_COUNT];
        Map<Long, Long> lastSeenByCard = new HashMap<>();
        Map<Long, Long> boundaryByCard = new HashMap<>();
        for (CardLastReview cardLastReview : lastReviews) {
            // card_id is a nullable column, so boundary rows without a card
            // cannot be attributed and are skipped (in-window logs with a
            // null card_id are all counted as card 0 by convention)
            if (cardLastReview != null && cardLastReview.cardId != null) {
                boundaryByCard.put(cardLastReview.cardId, cardLastReview.lastDateTime);
            }
        }
        Collections.sort(windowLogs, (left, right) -> Long.compare(
                left.createdDateTime.getTime(), right.createdDateTime.getTime()));
        for (ReviewLog reviewLog : windowLogs) {
            // card_id is a nullable column, null card ids count as one card
            long cardId = reviewLog.cardId == null ? 0L : reviewLog.cardId;
            long reviewTimeMs = reviewLog.createdDateTime.getTime();
            Long previousTimeMs = lastSeenByCard.get(cardId);
            if (previousTimeMs == null) {
                // guard: only trust the boundary when it really lies
                // before this review
                Long boundaryTimeMs = boundaryByCard.get(cardId);
                if (boundaryTimeMs != null && boundaryTimeMs < reviewTimeMs) {
                    previousTimeMs = boundaryTimeMs;
                }
            }
            if (previousTimeMs != null) {
                int dayDiff = dayNumber(reviewLog.createdDateTime)
                        - dayNumber(new Date(previousTimeMs));
                int bucket = trueRetentionBucket(dayDiff);
                stats.trueRetention.bucketTotalCounts[bucket]++;
                if (reviewLog.grade != ReviewScheduler.GRADE_AGAIN) {
                    stats.trueRetention.bucketCorrectCounts[bucket]++;
                }
            } else {
                stats.trueRetention.newCardCount++;
            }
            lastSeenByCard.put(cardId, reviewTimeMs);
        }
    }

    /**
     * @return the true-retention bucket for the given whole-day gap:
     * less than 1 day, 1..7 days, 8..30 days, or more than 30 days
     */
    private static int trueRetentionBucket(int dayDiff) {
        if (dayDiff < 1) {
            return 0;
        }
        if (dayDiff <= 7) {
            return 1;
        }
        if (dayDiff <= 30) {
            return 2;
        }
        return 3;
    }

    /**
     * Longest run of consecutive review days ending today, or ending yesterday
     * when today has no review yet (the day is not over); 0 when neither
     */
    private static int computeStreak(Set<Integer> reviewDaySet) {
        int firstDay = reviewDaySet.contains(0) ? 0 : -1;
        if (!reviewDaySet.contains(firstDay)) {
            return 0;
        }
        int streak = 0;
        int day = firstDay;
        while (reviewDaySet.contains(day)) {
            streak++;
            day--;
        }
        return streak;
    }

    /**
     * @return a stable day number of the given date: the timestamp normalized
     * to its local calendar day (zeroed hour/minute/second/millisecond) and
     * converted to whole days, independent of DST shifts between two days
     */
    private static int dayNumber(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long localMidnightMs = calendar.getTimeInMillis()
                + calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);
        long dayNumber = localMidnightMs / ONE_DAY_MS;
        if (localMidnightMs < 0 && localMidnightMs % ONE_DAY_MS != 0) {
            // floor division, keeps pre-1970 timestamps on the correct day
            dayNumber--;
        }
        return (int) dayNumber;
    }
}
