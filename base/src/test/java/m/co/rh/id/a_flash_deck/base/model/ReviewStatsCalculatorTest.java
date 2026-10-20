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

    private ReviewLog reviewLog(int dayDiff, int grade, long timeBeforeRevealMs) {
        ReviewLog reviewLog = reviewLog(dayDiff, grade);
        reviewLog.timeBeforeRevealMs = timeBeforeRevealMs;
        return reviewLog;
    }

    private ReviewLog reviewLog(long cardId, int dayDiff, int grade) {
        ReviewLog reviewLog = reviewLog(dayDiff, grade);
        reviewLog.cardId = cardId;
        return reviewLog;
    }

    private ReviewLog reviewLog(long cardId, int dayDiff, int grade,
                                long timeBeforeRevealMs) {
        ReviewLog reviewLog = reviewLog(cardId, dayDiff, grade);
        reviewLog.timeBeforeRevealMs = timeBeforeRevealMs;
        return reviewLog;
    }

    private ReviewLog reviewLogAt(int dayDiff, int hourOfDay, int grade) {
        ReviewLog reviewLog = reviewLog(dayDiff, grade);
        reviewLog.createdDateTime = dateAtDayHour(dayDiff, hourOfDay, 0);
        return reviewLog;
    }

    private ReviewLog reviewLogAt(int dayDiff, int hourOfDay, int grade,
                                  long timeBeforeRevealMs) {
        ReviewLog reviewLog = reviewLog(dayDiff, grade, timeBeforeRevealMs);
        reviewLog.createdDateTime = dateAtDayHour(dayDiff, hourOfDay, 0);
        return reviewLog;
    }

    private ReviewLog reviewLogAt(long cardId, int dayDiff, int hourOfDay, int grade) {
        ReviewLog reviewLog = reviewLog(cardId, dayDiff, grade);
        reviewLog.createdDateTime = dateAtDayHour(dayDiff, hourOfDay, 0);
        return reviewLog;
    }

    private CardLastReview cardLastReview(long cardId, int dayDiff) {
        CardLastReview cardLastReview = new CardLastReview();
        cardLastReview.cardId = cardId;
        cardLastReview.lastDateTime = dateAtDayHour(dayDiff, 8, 0).getTime();
        return cardLastReview;
    }

    private List<ReviewLog> reviewLogs(ReviewLog... reviewLogs) {
        return new ArrayList<>(Arrays.asList(reviewLogs));
    }

    @Test
    public void compute_emptyInput_allZeros() {
        ReviewStats stats = ReviewStatsCalculator.compute(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0,
                new ArrayList<>(), 0, new Date(NOW_MS));
        assertEquals(ReviewStatsCalculator.DAILY_REVIEW_DAYS, stats.dailyReviewCounts.length);
        assertEquals(ReviewStatsCalculator.DAILY_REVIEW_DAYS,
                stats.dailyAvgSpeedMs.length);
        assertEquals(ReviewStatsCalculator.DUE_FORECAST_DAYS, stats.dailyDueCounts.length);
        for (int count : stats.dailyReviewCounts) {
            assertEquals(0, count);
        }
        for (int count : stats.dailyAvgSpeedMs) {
            assertEquals(0, count);
        }
        for (int count : stats.dailyDueCounts) {
            assertEquals(0, count);
        }
        assertEquals(0, stats.todayReviewCount);
        assertEquals(0, stats.streakDays);
        assertEquals(0.0, stats.retentionPercent, 0.000001);
        assertEquals(0, stats.retentionTotal);
        assertEquals(0, stats.speedSampleCount);
        assertEquals(0L, stats.medianSpeedMs);
        assertEquals(0L, stats.avgSpeedMs);
        assertEquals(ReviewStatsCalculator.TRUE_RETENTION_BUCKET_COUNT,
                stats.trueRetention.bucketCorrectCounts.length);
        for (int count : stats.trueRetention.bucketCorrectCounts) {
            assertEquals(0, count);
        }
        for (int count : stats.trueRetention.bucketTotalCounts) {
            assertEquals(0, count);
        }
        assertEquals(0, stats.trueRetention.newCardCount);
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
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
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
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
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
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 2, new Date(NOW_MS));
        // 2021-02-28 is day -15 relative to 2021-03-15, 2021-03-01 is day -14
        assertEquals(1, stats.dailyReviewCounts[14]);
        assertEquals(1, stats.dailyReviewCounts[15]);
        assertEquals(2, sum(stats.dailyReviewCounts));
    }

    @Test
    public void compute_streak_todayOnly() {
        List<ReviewLog> logs = reviewLogs(reviewLog(0, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 1, new Date(NOW_MS));
        assertEquals(1, stats.streakDays);
    }

    @Test
    public void compute_streak_consecutiveDays() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD),
                reviewLog(-1, ReviewScheduler.GRADE_GOOD),
                reviewLog(-2, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
        assertEquals(3, stats.streakDays);
    }

    @Test
    public void compute_streak_gapBeforeToday_countsFromToday() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD),
                reviewLog(-2, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 2, new Date(NOW_MS));
        assertEquals(1, stats.streakDays);
    }

    @Test
    public void compute_streak_noReviewToday_yesterdayStillCounts() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(-1, ReviewScheduler.GRADE_GOOD),
                reviewLog(-2, ReviewScheduler.GRADE_GOOD),
                reviewLog(-3, ReviewScheduler.GRADE_GOOD));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 3, new Date(NOW_MS));
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
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 2, new Date(NOW_MS));
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
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 4, new Date(NOW_MS));
        // the 40 days old AGAIN review is outside the window
        assertEquals(3, stats.retentionTotal);
        assertEquals((2.0 / 3.0) * 100.0, stats.retentionPercent, 0.000001);
    }

    @Test
    public void compute_retention_noReviews_zeroDivisionSafe() {
        ReviewStats stats = ReviewStatsCalculator.compute(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0,
                new ArrayList<>(), 0, new Date(NOW_MS));
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
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 6, new Date(NOW_MS));
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
                new ArrayList<>(), new ArrayList<>(), dueDateTimes, 4,
                new ArrayList<>(), 0, new Date(NOW_MS));
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
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0,
                deckDueCounts, 12, new Date(NOW_MS));
        assertEquals(1, stats.deckDueCounts.size());
        assertEquals(7L, stats.deckDueCounts.get(0).deckId);
        assertEquals(3, stats.deckDueCounts.get(0).count);
        assertEquals(12, stats.totalReviews);
    }

    // ---------- review speed (time before reveal) ----------

    @Test
    public void compute_speed_meanMedianAndPerDay() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 5_000L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 15_000L),
                reviewLog(-1, ReviewScheduler.GRADE_GOOD, 20_000L));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 3,
                new Date(NOW_MS));
        assertEquals(3, stats.speedSampleCount);
        // mean of 5000 + 15000 + 20000, truncated to whole ms
        assertEquals(13_333L, stats.avgSpeedMs);
        // odd sample count: middle sample of the sorted samples
        assertEquals(15_000L, stats.medianSpeedMs);
        // oldest first: day -1 sits at index 28, today at index 29
        assertEquals(0, stats.dailyAvgSpeedMs[27]);
        assertEquals(20_000, stats.dailyAvgSpeedMs[28]);
        assertEquals(10_000, stats.dailyAvgSpeedMs[29]);
    }

    @Test
    public void compute_speed_medianEvenSampleCount_isLowerMiddle() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 1_000L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 4_000L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 2_000L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 3_000L));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 4,
                new Date(NOW_MS));
        assertEquals(4, stats.speedSampleCount);
        // even sample count: lower middle of the sorted samples [1,2,3,4]
        assertEquals(2_000L, stats.medianSpeedMs);
        assertEquals(2_500L, stats.avgSpeedMs);
    }

    @Test
    public void compute_speed_outOfRangeSamplesExcluded() {
        // negative (backwards clock change) and 0 (pre-timing rows) are
        // invalid, exactly 10 minutes is still valid, just above is not
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD, -5_000L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 0L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 600_001L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 600_000L),
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 1L));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 5,
                new Date(NOW_MS));
        assertEquals(2, stats.speedSampleCount);
        // mean of 600000 + 1, truncated to whole ms
        assertEquals(300_000L, stats.avgSpeedMs);
        // even sample count: lower middle of [1, 600000]
        assertEquals(1L, stats.medianSpeedMs);
        assertEquals(300_000, stats.dailyAvgSpeedMs[29]);
    }

    @Test
    public void compute_speed_noValidSamples_allZeros() {
        List<ReviewLog> logs = reviewLogs(
                reviewLog(0, ReviewScheduler.GRADE_GOOD, 0L),
                reviewLog(-1, ReviewScheduler.GRADE_GOOD, -1_000L));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 2,
                new Date(NOW_MS));
        assertEquals(0, stats.speedSampleCount);
        assertEquals(0L, stats.medianSpeedMs);
        assertEquals(0L, stats.avgSpeedMs);
        for (int avgSpeedMs : stats.dailyAvgSpeedMs) {
            assertEquals(0, avgSpeedMs);
        }
    }

    @Test
    public void compute_speed_dayBucketing_monthBoundary() {
        // same calendar-day boundary as the daily-count test above:
        // 2021-02-28 23:00 UTC and 2021-03-01 00:00 UTC
        List<ReviewLog> logs = reviewLogs(
                reviewLogAt(-15, 23, ReviewScheduler.GRADE_GOOD, 10_000L),
                reviewLogAt(-14, 0, ReviewScheduler.GRADE_GOOD, 20_000L));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), 2,
                new Date(NOW_MS));
        assertEquals(10_000, stats.dailyAvgSpeedMs[14]);
        assertEquals(20_000, stats.dailyAvgSpeedMs[15]);
    }

    // ---------- true retention ----------

    @Test
    public void compute_trueRetention_bucketEdges() {
        // window reviews ordered "wrongly" on purpose; the calculator
        // iterates oldest -> newest internally. Each bucket edge is
        // covered once: 0, 1, 7, 8, 30 and 31 whole days
        List<ReviewLog> logs = reviewLogs(
                // card 1: boundary day -60 + window review day -29 = 31 days
                reviewLog(1L, -29, ReviewScheduler.GRADE_GOOD),
                // card 2: boundary day -58 + window review day -28 = 30 days
                reviewLog(2L, -28, ReviewScheduler.GRADE_GOOD),
                // card 3: window pair day -8 -> day 0 = 8 days
                reviewLog(3L, -8, ReviewScheduler.GRADE_GOOD),
                reviewLog(3L, 0, ReviewScheduler.GRADE_GOOD),
                // card 4: window pair day -7 -> day 0 = 7 days
                reviewLog(4L, -7, ReviewScheduler.GRADE_EASY),
                reviewLog(4L, 0, ReviewScheduler.GRADE_GOOD),
                // card 5: window pair day -1 -> day 0 = 1 day
                reviewLog(5L, -1, ReviewScheduler.GRADE_GOOD),
                reviewLog(5L, 0, ReviewScheduler.GRADE_GOOD),
                // card 6: AGAIN requeued in the same session, re-reviewed
                // the same day = 0 days (<1d)
                reviewLog(6L, 0, ReviewScheduler.GRADE_AGAIN),
                reviewLogAt(6L, 0, 9, ReviewScheduler.GRADE_GOOD),
                // card 7: boundary day -30 + AGAIN review day -4 = 26 days,
                // incorrect, so not a correct recall
                reviewLog(7L, -4, ReviewScheduler.GRADE_AGAIN));
        List<CardLastReview> boundary = Arrays.asList(
                cardLastReview(1L, -60),
                cardLastReview(2L, -58),
                // a null entry must be tolerated
                null,
                cardLastReview(7L, -30));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, boundary, new ArrayList<>(), 0, new ArrayList<>(), 11,
                new Date(NOW_MS));
        // first-ever reviews in the window: cards 3, 4, 5 (first reviews)
        // and 6 (first review), excluded from the buckets
        assertEquals(4, stats.trueRetention.newCardCount);
        assertEquals(1, stats.trueRetention.bucketTotalCounts[0]);
        assertEquals(1, stats.trueRetention.bucketCorrectCounts[0]);
        // 1 day (card 5) and 7 days (card 4)
        assertEquals(2, stats.trueRetention.bucketTotalCounts[1]);
        assertEquals(2, stats.trueRetention.bucketCorrectCounts[1]);
        // 30 days (card 2), 8 days (card 3), 26 days + again (card 7)
        assertEquals(3, stats.trueRetention.bucketTotalCounts[2]);
        assertEquals(2, stats.trueRetention.bucketCorrectCounts[2]);
        // 31 days (card 1)
        assertEquals(1, stats.trueRetention.bucketTotalCounts[3]);
        assertEquals(1, stats.trueRetention.bucketCorrectCounts[3]);
    }

    @Test
    public void compute_trueRetention_boundaryNotEarlier_treatedAsFirstEver() {
        // the guard: a boundary value at or after the review must be
        // ignored, the review counts as first-ever instead
        List<ReviewLog> logs = reviewLogs(
                reviewLog(1L, -20, ReviewScheduler.GRADE_GOOD),
                reviewLog(2L, -20, ReviewScheduler.GRADE_GOOD));
        List<CardLastReview> boundary = Arrays.asList(
                cardLastReview(1L, 0),
                cardLastReview(2L, -20));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, boundary, new ArrayList<>(), 0, new ArrayList<>(), 2,
                new Date(NOW_MS));
        assertEquals(2, stats.trueRetention.newCardCount);
        assertEquals(0, sum(stats.trueRetention.bucketTotalCounts));
    }

    @Test
    public void compute_trueRetention_nullBoundary_listTolerated() {
        ReviewStats stats = ReviewStatsCalculator.compute(
                null, null, null, 0, null, 0, new Date(NOW_MS));
        assertEquals(0, stats.trueRetention.newCardCount);
        assertEquals(ReviewStatsCalculator.TRUE_RETENTION_BUCKET_COUNT,
                stats.trueRetention.bucketTotalCounts.length);
        for (int count : stats.trueRetention.bucketTotalCounts) {
            assertEquals(0, count);
        }
        for (int count : stats.trueRetention.bucketCorrectCounts) {
            assertEquals(0, count);
        }
    }

    @Test
    public void compute_trueRetention_multiWindowReview_usesInWindowPrevious() {
        // a card reviewed 3 times in the window keeps chaining: the second
        // and third review use the previous in-window review, only the
        // first uses the boundary
        List<ReviewLog> logs = reviewLogs(
                reviewLog(1L, -29, ReviewScheduler.GRADE_GOOD),
                reviewLog(1L, -15, ReviewScheduler.GRADE_GOOD),
                reviewLog(1L, 0, ReviewScheduler.GRADE_GOOD));
        List<CardLastReview> boundary = Arrays.asList(
                // more than 30 days before the first window review
                cardLastReview(1L, -70));
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, boundary, new ArrayList<>(), 0, new ArrayList<>(), 3,
                new Date(NOW_MS));
        // first window review: 41 whole days after the boundary (>30d)
        assertEquals(0, stats.trueRetention.newCardCount);
        assertEquals(1, stats.trueRetention.bucketTotalCounts[3]);
        assertEquals(1, stats.trueRetention.bucketCorrectCounts[3]);
        // later reviews chain from the in-window previous:
        // -29 -> -15 = 14 days (8-30d), -15 -> 0 = 15 days (8-30d)
        assertEquals(2, stats.trueRetention.bucketTotalCounts[2]);
        assertEquals(2, stats.trueRetention.bucketCorrectCounts[2]);
    }

    @Test
    public void reviewWindowStartMs_localMidnightOfFirstWindowDay() {
        // today is 2021-03-15 (UTC-pinned); the calendar window covers the
        // days -29..0, so it starts at local midnight of 2021-02-14. A
        // rolling "now minus 30x24h" cutoff (2021-02-13 10:30) would sit
        // almost a day earlier and its band up to the calendar start
        // would be silently dropped by a "reviews before the window"
        // query feeding the boundary
        long expected = utcTime(2021, Calendar.FEBRUARY, 14, 0, 0, 0);
        assertEquals(expected,
                ReviewStatsCalculator.reviewWindowStartMs(new Date(NOW_MS)));
    }

    @Test
    public void compute_trueRetention_previousReviewInBoundaryBand_isBucketed() {
        // StatsCmd feeds the boundary with the calendar-day cutoff, so a
        // card's previous review in the dropped band just before the
        // window start must drive the gap instead of counting the card
        // as first-ever
        long windowStartMs =
                ReviewStatsCalculator.reviewWindowStartMs(new Date(NOW_MS));
        CardLastReview bandBoundary = new CardLastReview();
        bandBoundary.cardId = 1L;
        // previous review one minute before the window start
        bandBoundary.lastDateTime = windowStartMs - 60_000L;
        // findReviewLogsSince(rolling) still returns the band review; the
        // calculator's day filter must drop it, leaving only the boundary
        ReviewLog bandLog = new ReviewLog();
        bandLog.cardId = 1L;
        bandLog.grade = ReviewScheduler.GRADE_GOOD;
        bandLog.createdDateTime = new Date(bandBoundary.lastDateTime);
        List<ReviewLog> logs = reviewLogs(
                bandLog,
                reviewLog(1L, -29, ReviewScheduler.GRADE_GOOD));
        List<CardLastReview> boundary = new ArrayList<>();
        boundary.add(bandBoundary);
        ReviewStats stats = ReviewStatsCalculator.compute(
                logs, boundary, new ArrayList<>(), 0, new ArrayList<>(), 2,
                new Date(NOW_MS));
        // band review is day -30 (outside the calendar window), so the
        // day -29 review is exactly 1 whole day after it (1-7d bucket),
        // not first-ever
        assertEquals(0, stats.trueRetention.newCardCount);
        assertEquals(1, stats.trueRetention.bucketTotalCounts[1]);
        assertEquals(1, stats.trueRetention.bucketCorrectCounts[1]);
        assertEquals(0, stats.trueRetention.bucketTotalCounts[0]);
        assertEquals(0, stats.trueRetention.bucketTotalCounts[2]);
        assertEquals(0, stats.trueRetention.bucketTotalCounts[3]);
    }

    private static int sum(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }
}
