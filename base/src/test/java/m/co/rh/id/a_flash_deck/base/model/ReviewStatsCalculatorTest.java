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

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import m.co.rh.id.a_flash_deck.base.entity.ReviewLog;

public class ReviewStatsCalculatorTest {

    // 2021-03-15 10:30 UTC, a Monday in the middle of a non-leap March.
    // Built with an explicit UTC calendar (static init runs before setUp
    // pins the default timezone) so the instant is machine-independent.
    private static final long NOW_MS = utcTime(2021, Calendar.MARCH,
            15, 10, 30, 0);

    private TimeZone mOriginalTimeZone;

    private static long utcTime(int year, int month, int day,
                                int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }

    @Before
    public void setUp() {
        // pin to UTC so day bucketing is deterministic and DST-free
        mOriginalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(mOriginalTimeZone);
    }

    private Date dateAtDayHour(int dayDiff, int hourOfDay, int minute) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(NOW_MS);
        calendar.add(Calendar.DAY_OF_MONTH, dayDiff);
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private ReviewLog reviewLog(int dayDiff, int grade) {
        ReviewLog reviewLog = new ReviewLog();
        reviewLog.cardId = 1L;
        reviewLog.deckId = 1L;
        reviewLog.grade = grade;
        reviewLog.createdDateTime = dateAtDayHour(dayDiff, 8, 0);
        return reviewLog;
    }

    private ReviewLog reviewLogAt(int dayDiff, int hourOfDay, int grade) {
        ReviewLog reviewLog = reviewLog(dayDiff, grade);
        reviewLog.createdDateTime = dateAtDayHour(dayDiff, hourOfDay, 0);
        return reviewLog;
    }

    private List<ReviewLog> reviewLogs(ReviewLog... reviewLogs) {
        return new ArrayList<>(Arrays.asList(reviewLogs));
    }

    @Test
    public void compute_emptyInput_allZeros() {
        ReviewStats stats = ReviewStatsCalculator.compute(
                new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 0,
                new Date(NOW_MS));
        assertEquals(ReviewStatsCalculator.DAILY_REVIEW_DAYS, stats.dailyReviewCounts.length);
        assertEquals(ReviewStatsCalculator.DUE_FORECAST_DAYS, stats.dailyDueCounts.length);
        for (int count : stats.dailyReviewCounts) {
            assertEquals(0, count);
        }
        for (int count : stats.dailyDueCounts) {
            assertEquals(0, count);
        }
        assertEquals(0, stats.todayReviewCount);
        assertEquals(0, stats.streakDays);
        assertEquals(0.0, stats.retentionPercent, 0.000001);
        assertEquals(0, stats.retentionTotal);
        for (int count : stats.todayGradeCounts) {
            assertEquals(0, count);
        }
        assertEquals(0, stats.dueLaterCount);
        assertEquals(0, stats.newCardCount);
        assertEquals(0, stats.totalReviews);
        assertEquals(0, stats.deckDueCounts.size());
    }

    @Test
    public void compute_dayBucketing_oldestFirstTodayLast() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD),
                reviewLog(-1, ReviewScheduler.GRADE_GOOD),
                reviewLog(-29, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
        assertEquals(1, stats.dailyReviewCounts[0]);
        assertEquals(1, stats.dailyReviewCounts[28]);
        assertEquals(1, stats.dailyReviewCounts[29]);
        assertEquals(1, stats.todayReviewCount);
        assertEquals(3, sum(stats.dailyReviewCounts));
    }

    @Test
    public void compute_reviewOlderThan30Days_excluded() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(-30, ReviewScheduler.GRADE_GOOD),
                reviewLog(-31, ReviewScheduler.GRADE_GOOD),
                reviewLog(0, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
        assertEquals(1, sum(stats.dailyReviewCounts));
        assertEquals(1, stats.retentionTotal);
    }

    @Test
    public void compute_monthBoundary_bucketsByCalendarDay() {
        // review at 2021-02-28 23:00 UTC is the calendar day before
        // 2021-03-01, not the "24h ago" day
        List<ReviewLog> logs = reviewLogs(
                reviewLogAt(-15, 23, ReviewScheduler.GRADE_GOOD),
                reviewLogAt(-14, 0, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 2, new Date(NOW_MS));
        // 2021-02-28 is day -15 relative to 2021-03-15, 2021-03-01 is day -14
        assertEquals(1, stats.dailyReviewCounts[14]);
        assertEquals(1, stats.dailyReviewCounts[15]);
        assertEquals(2, sum(stats.dailyReviewCounts));
    }

    @Test
    public void compute_streak_todayOnly() {
        List<ReviewLog> logs = reviewLogs(reviewLog(0, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 1, new Date(NOW_MS));
        assertEquals(1, stats.streakDays);
    }

    @Test
    public void compute_streak_consecutiveDays() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD),
                reviewLog(-1, ReviewScheduler.GRADE_GOOD),
                reviewLog(-2, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
        assertEquals(3, stats.streakDays);
    }

    @Test
    public void compute_streak_gapBeforeToday_countsFromToday() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD),
                reviewLog(-2, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 2, new Date(NOW_MS));
        assertEquals(1, stats.streakDays);
    }

    @Test
    public void compute_streak_noReviewToday_yesterdayStillCounts() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(-1, ReviewScheduler.GRADE_GOOD),
                reviewLog(-2, ReviewScheduler.GRADE_GOOD),
                reviewLog(-3, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
        assertEquals(3, stats.streakDays);
    }

    @Test
    public void compute_streak_noReviewTodayOrYesterday_isZero() {
        // a review two days ago does not keep the streak alive once
        // both yesterday and today have no reviews
        List<ReviewLog> logs = reviewLogs(
                reviewLog(-2, ReviewScheduler.GRADE_GOOD),
                reviewLog(-3, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 2, new Date(NOW_MS));
        assertEquals(0, stats.streakDays);
    }

    @Test
    public void compute_retention_percentAndTotal() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_AGAIN),
                reviewLog(0, ReviewScheduler.GRADE_GOOD),
                reviewLog(-1, ReviewScheduler.GRADE_EASY),
                reviewLog(-40, ReviewScheduler.GRADE_AGAIN));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 4, new Date(NOW_MS));
        // the 40 days old AGAIN review is outside the window
        assertEquals(3, stats.retentionTotal);
        assertEquals((2.0 / 3.0) * 100.0, stats.retentionPercent, 0.000001);
    }

    @Test
    public void compute_retention_noReviews_zeroDivisionSafe() {
        ReviewStats stats = ReviewStatsCalculator.compute(
                new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 0,
                new Date(NOW_MS));
        assertEquals(0, stats.retentionTotal);
        assertEquals(0.0, stats.retentionPercent, 0.000001);
    }

    @Test
    public void compute_todayGradeCounts_perGrade() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_AGAIN),
                reviewLog(0, ReviewScheduler.GRADE_AGAIN),
                reviewLog(0, ReviewScheduler.GRADE_GOOD),
                reviewLog(0, ReviewScheduler.GRADE_EASY),
                reviewLog(-1, ReviewScheduler.GRADE_HARD),
                reviewLog(0, 99));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), 0, new ArrayList<>(), 6, new Date(NOW_MS));
        assertEquals(4, stats.todayGradeCounts.length);
        assertEquals(2, stats.todayGradeCounts[ReviewScheduler.GRADE_AGAIN]);
        assertEquals(0, stats.todayGradeCounts[ReviewScheduler.GRADE_HARD]);
        assertEquals(1, stats.todayGradeCounts[ReviewScheduler.GRADE_GOOD]);
        assertEquals(1, stats.todayGradeCounts[ReviewScheduler.GRADE_EASY]);
    }

    @Test
    public void compute_dueForecast_bucketsAndLater() {
        List<Date> dueDateTimes = Arrays.asList(
                dateAtDayHour(0, 9, 0),
                dateAtDayHour(5, 9, 0),
                dateAtDayHour(13, 9, 0),
                dateAtDayHour(14, 9, 0),
                dateAtDayHour(100, 9, 0),
                // already overdue, not part of the forecast
                dateAtDayHour(-1, 9, 0));
        ReviewStats stats = ReviewStatsCalculator.compute(
                new ArrayList<>(), dueDateTimes, 4, new ArrayList<>(), 0,
                new Date(NOW_MS));
        assertEquals(1, stats.dailyDueCounts[0]);
        assertEquals(1, stats.dailyDueCounts[5]);
        assertEquals(1, stats.dailyDueCounts[13]);
        assertEquals(2, stats.dueLaterCount);
        assertEquals(4, stats.newCardCount);
        assertEquals(ReviewStatsCalculator.DUE_FORECAST_DAYS, stats.dailyDueCounts.length);
    }

    @Test
    public void compute_deckDueCountsPassedThrough() {
        DeckDueCount deckDueCount = new DeckDueCount();
        deckDueCount.deckId = 7L;
        deckDueCount.count = 3;
        List<DeckDueCount> deckDueCounts = new ArrayList<>();
        deckDueCounts.add(deckDueCount);
        ReviewStats stats = ReviewStatsCalculator.compute(
                new ArrayList<>(), new ArrayList<>(), 0, deckDueCounts, 12,
                new Date(NOW_MS));
        assertEquals(1, stats.deckDueCounts.size());
        assertEquals(7L, stats.deckDueCounts.get(0).deckId);
        assertEquals(3, stats.deckDueCounts.get(0).count);
        assertEquals(12, stats.totalReviews);
    }

    private static int sum(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }
}
