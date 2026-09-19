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
import java.util.HashSet;
import java.util.List;
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

    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

    private ReviewStatsCalculator() {
    }

    /**
     * Computes the review statistics from the given raw data.
     * Null lists are tolerated and treated as empty.
     *
     * @param reviewLogs    review history entries (any range, only the last
     *                      30 days are used)
     * @param dueDateTimes  due date time of every not suspended card that has
     *                      one scheduled
     * @param newCardCount  count of new/never studied cards across all decks
     * @param deckDueCounts per-deck due/new counts
     * @param totalReviews  total review count ever recorded
     * @param now           reference "today" used for all day bucketing
     * @return the computed statistics snapshot
     */
    public static ReviewStats compute(List<ReviewLog> reviewLogs, List<Date> dueDateTimes,
                                      int newCardCount, List<DeckDueCount> deckDueCounts,
                                      int totalReviews, Date now) {
        List<ReviewLog> logs = reviewLogs == null
                ? Collections.emptyList() : reviewLogs;
        List<Date> dueDates = dueDateTimes == null
                ? Collections.emptyList() : dueDateTimes;
        List<DeckDueCount> deckDueCountList = deckDueCounts == null
                ? Collections.emptyList() : deckDueCounts;

        ReviewStats stats = new ReviewStats();
        stats.dailyReviewCounts = new int[DAILY_REVIEW_DAYS];
        stats.todayGradeCounts = new int[ReviewScheduler.GRADE_EASY + 1];
        stats.dailyDueCounts = new int[DUE_FORECAST_DAYS];
        stats.newCardCount = newCardCount;
        stats.totalReviews = totalReviews;
        stats.deckDueCounts = new ArrayList<>(deckDueCountList);

        int todayDayNumber = dayNumber(now);
        Set<Integer> reviewDaySet = new HashSet<>();
        int retentionNonAgainCount = 0;
        int retentionTotalCount = 0;
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
            stats.dailyReviewCounts[DAILY_REVIEW_DAYS - 1 + dayDiff]++;
            retentionTotalCount++;
            if (dayDiff == 0 && reviewLog.grade >= 0
                    && reviewLog.grade < stats.todayGradeCounts.length) {
                stats.todayGradeCounts[reviewLog.grade]++;
            }
            if (reviewLog.grade != ReviewScheduler.GRADE_AGAIN) {
                retentionNonAgainCount++;
            }
        }
        stats.todayReviewCount = stats.dailyReviewCounts[DAILY_REVIEW_DAYS - 1];
        stats.streakDays = computeStreak(reviewDaySet);

        stats.retentionTotal = retentionTotalCount;
        stats.retentionPercent = stats.retentionTotal == 0 ? 0.0
                : retentionNonAgainCount * 100.0 / stats.retentionTotal;

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
