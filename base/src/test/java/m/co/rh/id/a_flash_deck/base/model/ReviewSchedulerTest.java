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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;

import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;

public class ReviewSchedulerTest {

    private static final long BASE_TIME_MS = 1_700_000_000_000L;
    private static final double DELTA = 0.000001;

    @Test
    public void newCardReviewState_hasDefaultStateOfNewCard() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        assertEquals(1L, state.cardId.longValue());
        assertEquals(0.0, state.intervalDays, DELTA);
        assertEquals(ReviewScheduler.EASE_INIT, state.easeFactor, DELTA);
        assertEquals(0, state.repetitions);
        assertEquals(0, state.lapses);
        assertNull(state.dueDateTime);
        assertNull(state.lastReviewDateTime);
    }

    @Test
    public void schedule_newCardGood_intervalIsOneDay() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        Date now = new Date(BASE_TIME_MS);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD, now);
        assertEquals(1.0, state.intervalDays, DELTA);
        assertEquals(1, state.repetitions);
        assertEquals(BASE_TIME_MS + ReviewScheduler.ONE_DAY_MS,
                state.dueDateTime.getTime());
        assertEquals(now, state.lastReviewDateTime);
    }

    @Test
    public void schedule_newCardEasy_intervalIsFourDays() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_EASY,
                new Date(BASE_TIME_MS));
        assertEquals(4.0, state.intervalDays, DELTA);
        assertEquals(ReviewScheduler.EASE_INIT + 0.15, state.easeFactor, DELTA);
    }

    @Test
    public void schedule_newCardAgain_dueInTenMinutesAndCountsLapse() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_AGAIN,
                new Date(BASE_TIME_MS));
        assertEquals(0, state.repetitions);
        assertEquals(1, state.lapses);
        assertEquals(0.0, state.intervalDays, DELTA);
        assertEquals(BASE_TIME_MS + ReviewScheduler.AGAIN_DUE_DELAY_MS,
                state.dueDateTime.getTime());
    }

    @Test
    public void schedule_goodStreak_intervalGrowsByEaseFactor() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                new Date(BASE_TIME_MS));
        assertEquals(1.0, state.intervalDays, DELTA);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                new Date(BASE_TIME_MS + ReviewScheduler.ONE_DAY_MS));
        assertEquals(2.5, state.intervalDays, DELTA);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                new Date(BASE_TIME_MS + 4 * ReviewScheduler.ONE_DAY_MS));
        assertEquals(6.25, state.intervalDays, DELTA);
    }

    @Test
    public void schedule_againAfterStreak_resetsIntervalAndCountsLapse() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                new Date(BASE_TIME_MS));
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                new Date(BASE_TIME_MS + ReviewScheduler.ONE_DAY_MS));
        assertEquals(2, state.repetitions);
        assertEquals(2.5, state.intervalDays, DELTA);
        long nowMs = BASE_TIME_MS + 4 * ReviewScheduler.ONE_DAY_MS;
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_AGAIN, new Date(nowMs));
        assertEquals(0, state.repetitions);
        assertEquals(1, state.lapses);
        assertEquals(0.0, state.intervalDays, DELTA);
        assertEquals(nowMs + ReviewScheduler.AGAIN_DUE_DELAY_MS,
                state.dueDateTime.getTime());
    }

    @Test
    public void schedule_manyEasy_easeClampedAtMax() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        for (int i = 0; i < 20; i++) {
            ReviewScheduler.schedule(state, ReviewScheduler.GRADE_EASY,
                    new Date(BASE_TIME_MS + i * ReviewScheduler.ONE_DAY_MS));
        }
        assertEquals(ReviewScheduler.EASE_MAX, state.easeFactor, DELTA);
    }

    @Test
    public void schedule_manyAgain_easeClampedAtMin() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        for (int i = 0; i < 20; i++) {
            ReviewScheduler.schedule(state, ReviewScheduler.GRADE_AGAIN,
                    new Date(BASE_TIME_MS + i * ReviewScheduler.ONE_DAY_MS));
        }
        assertEquals(ReviewScheduler.EASE_MIN, state.easeFactor, DELTA);
        assertEquals(20, state.lapses);
    }

    @Test
    public void schedule_longGoodStreak_dueDateCappedAt365Days() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        long nowMs = BASE_TIME_MS;
        for (int i = 0; i < 20; i++) {
            ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                    new Date(nowMs));
            nowMs += ReviewScheduler.ONE_DAY_MS;
        }
        assertTrue(state.intervalDays > ReviewScheduler.INTERVAL_CAP_DAYS);
        assertEquals(ReviewScheduler.INTERVAL_CAP_DAYS * ReviewScheduler.ONE_DAY_MS,
                state.dueDateTime.getTime() - (nowMs - ReviewScheduler.ONE_DAY_MS));
    }

    @Test
    public void schedule_newCardHard_intervalIsOneDay() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_HARD,
                new Date(BASE_TIME_MS));
        assertEquals(1.0, state.intervalDays, DELTA);
        assertEquals(1, state.repetitions);
        assertEquals(ReviewScheduler.EASE_INIT - 0.15, state.easeFactor, DELTA);
        assertEquals(BASE_TIME_MS + ReviewScheduler.ONE_DAY_MS,
                state.dueDateTime.getTime());
    }

    @Test(expected = IllegalArgumentException.class)
    public void schedule_unknownGrade_throwsIllegalArgumentException() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, 99, new Date(BASE_TIME_MS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void schedule_unknownGrade4_throwsIllegalArgumentException() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, 4, new Date(BASE_TIME_MS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void schedule_unknownNegativeGrade_throwsIllegalArgumentException() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, -1, new Date(BASE_TIME_MS));
    }

    @Test
    public void schedule_goodAfterAgain_restartsAtOneDayInterval() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_AGAIN,
                new Date(BASE_TIME_MS));
        long nowMs = BASE_TIME_MS + ReviewScheduler.AGAIN_DUE_DELAY_MS;
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD, new Date(nowMs));
        assertEquals(1.0, state.intervalDays, DELTA);
        assertEquals(1, state.repetitions);
        assertEquals(nowMs + ReviewScheduler.ONE_DAY_MS,
                state.dueDateTime.getTime());
    }

    @Test
    public void schedule_easyAfterAgain_restartsAtFourDaysInterval() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_AGAIN,
                new Date(BASE_TIME_MS));
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_EASY,
                new Date(BASE_TIME_MS + ReviewScheduler.AGAIN_DUE_DELAY_MS));
        assertEquals(4.0, state.intervalDays, DELTA);
    }

    @Test
    public void schedule_hardAfterAgain_intervalIsOneDay() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_AGAIN,
                new Date(BASE_TIME_MS));
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_HARD,
                new Date(BASE_TIME_MS + ReviewScheduler.AGAIN_DUE_DELAY_MS));
        assertEquals(1.0, state.intervalDays, DELTA);
    }

    @Test
    public void schedule_hardOnNonNewCardWithIntervalOne_growsToTwelvePercent() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                new Date(BASE_TIME_MS));
        assertEquals(1.0, state.intervalDays, DELTA);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_HARD,
                new Date(BASE_TIME_MS + ReviewScheduler.ONE_DAY_MS));
        assertEquals(1.2, state.intervalDays, DELTA);
    }

    @Test
    public void schedule_intervalExactly365_dueIsExactlyNowPlus365Days() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_GOOD,
                new Date(BASE_TIME_MS));
        state.intervalDays = ReviewScheduler.INTERVAL_CAP_DAYS;
        long nowMs = BASE_TIME_MS + ReviewScheduler.ONE_DAY_MS;
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_HARD, new Date(nowMs));
        assertEquals(ReviewScheduler.INTERVAL_CAP_DAYS * ReviewScheduler.ONE_DAY_MS,
                state.dueDateTime.getTime() - nowMs);
    }

    @Test
    public void schedule_again_setsDefensiveCopyOfLastReviewDateTime() {
        CardReviewState state = ReviewScheduler.newCardReviewState(1L);
        Date now = new Date(BASE_TIME_MS);
        ReviewScheduler.schedule(state, ReviewScheduler.GRADE_AGAIN, now);
        assertEquals(BASE_TIME_MS, state.lastReviewDateTime.getTime());
        assertNotSame(now, state.lastReviewDateTime);
    }
}
