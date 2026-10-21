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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;
import java.util.Random;

import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;

/**
 * Golden vectors generated from a JS port of the FSRS-6 default parameters
 * validated against py-fsrs v6.0.1's own test suite, plus hand-rolled
 * property tests (dual-write column isolation, monotonicity, invariants).
 * Base time is 1_700_000_000_000 ms, gaps are whole days between reviews.
 */
public class FsrsSchedulerTest {

    private static final long BASE_TIME_MS = 1_700_000_000_000L;
    private static final double DELTA = 0.0001;
    private static final double DEFAULT_RETENTION = 0.9;

    /**
     * Grade runner that keeps an accumulating time cursor: each grade
     * advances the cursor by the given whole-day gap, then schedules with
     * FsrsScheduler and computes the resulting due date time.
     */
    private static class Runner {
        final CardReviewState state;
        final double desiredRetention;
        long nowMs;

        Runner(CardReviewState state, double desiredRetention) {
            this.state = state;
            this.desiredRetention = desiredRetention;
            this.nowMs = BASE_TIME_MS;
        }

        /**
         * @return the FSRS interval in days of this grade, computed via
         * FsrsScheduler.computeDueDate (AGAIN yields 0 with due +10 minutes)
         */
        Step grade(int grade, double gapDays) {
            nowMs += Math.round(gapDays * ReviewScheduler.ONE_DAY_MS);
            Date now = new Date(nowMs);
            FsrsScheduler.schedule(state, grade, now, desiredRetention);
            Date due = FsrsScheduler.computeDueDate(state, grade, now,
                    desiredRetention);
            return new Step(this, due, grade);
        }
    }

    private static class Step {
        final CardReviewState state;
        final Date due;
        final long nowMs;
        final int grade;

        Step(Runner runner, Date due, int grade) {
            this.state = runner.state;
            this.due = due;
            this.nowMs = runner.nowMs;
            this.grade = grade;
        }

        int intervalDays() {
            long delay = due.getTime() - nowMs;
            return (int) Math.round(delay / (double) ReviewScheduler.ONE_DAY_MS);
        }

        boolean isAgain() {
            return grade == ReviewScheduler.GRADE_AGAIN;
        }
    }

    private static void assertStep(Step step, double stability,
                                   double difficulty, int intervalDays) {
        assertEquals(stability, step.state.stability, DELTA);
        assertEquals(difficulty, step.state.difficulty, DELTA);
        if (step.isAgain()) {
            assertEquals(ReviewScheduler.AGAIN_DUE_DELAY_MS,
                    step.due.getTime() - step.nowMs);
        } else {
            assertEquals(intervalDays * ReviewScheduler.ONE_DAY_MS,
                    step.due.getTime() - step.nowMs);
        }
    }

    private static CardReviewState newCard() {
        return ReviewScheduler.newCardReviewState(1L);
    }

    @Test
    public void golden_v1_goodStreak() {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                3.260200, 4.884632, 3);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 3),
                11.326707, 4.868057, 11);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 3),
                19.684585, 4.851596, 20);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 14),
                52.219225, 4.835249, 52);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 45),
                142.850739, 4.819014, 143);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 135),
                381.734783, 4.802892, 382);
    }

    @Test
    public void golden_v2_lapseRecovery() {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                3.260200, 4.884632, 3);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 3),
                11.326707, 4.868057, 11);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 14),
                41.962614, 4.851596, 42);
        // AGAIN: forget path, due in 10 minutes
        assertStep(runner.grade(ReviewScheduler.GRADE_AGAIN, 45),
                4.410361, 7.217396, 0);
        // same-day GOOD after AGAIN: short-term path
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                4.580515, 7.184725, 5);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 2),
                8.230995, 7.152279, 8);
    }

    @Test
    public void golden_v3_mixed() {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                3.260200, 4.884632, 3);
        assertStep(runner.grade(ReviewScheduler.GRADE_HARD, 1),
                3.951379, 6.051488, 4);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 2),
                8.674097, 6.026861, 9);
        assertStep(runner.grade(ReviewScheduler.GRADE_EASY, 6),
                45.947331, 5.083226, 46);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 20),
                89.691682, 5.065281, 90);
        // same-day AGAIN: short-term path, due in 10 minutes
        assertStep(runner.grade(ReviewScheduler.GRADE_AGAIN, 0),
                13.385179, 7.330736, 0);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 3),
                18.354988, 7.297282, 18);
    }

    @Test
    public void golden_v4_sameDayChain() {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                3.260200, 4.884632, 3);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                3.536244, 4.868057, 4);
        assertStep(runner.grade(ReviewScheduler.GRADE_EASY, 0),
                8.054751, 3.664330, 8);
        assertStep(runner.grade(ReviewScheduler.GRADE_HARD, 0),
                3.610932, 5.121921, 4);
    }

    @Test
    public void schedule_fractionalDayGap_elapsedDaysFloorsToZero_shortTermPathRuns() {
        // a fractional-day gap (6 hours) must floor elapsedDays to 0, so the
        // short-term path runs instead of recall; the HARD short-term
        // increment has no floor (rating < GOOD), so stability decreases
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                3.260200, 4.884632, 3);
        assertStep(runner.grade(ReviewScheduler.GRADE_HARD, 0.25),
                1.664401, 6.051488, 2);
    }

    @Test
    public void golden_v5_easyStreak() {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_EASY, 0),
                16.150700, 2.482439, 16);
        // difficulty clamped at the minimum
        assertStep(runner.grade(ReviewScheduler.GRADE_EASY, 4),
                61.018370, 1.0, 61);
        assertStep(runner.grade(ReviewScheduler.GRADE_EASY, 12),
                199.343834, 1.0, 199);
    }

    @Test
    public void golden_v6_seededFromSm2History() {
        // SM-2 history: interval 30 days, ease 2.5, last review exactly
        // 30 days before the FSRS grade; the seed (S=30, D=2.8) is applied
        // inside the GOOD grade, then the normal recall path runs
        CardReviewState state = newCard();
        state.intervalDays = 30;
        state.easeFactor = 2.5;
        state.repetitions = 5;
        state.lastReviewDateTime =
                new Date(BASE_TIME_MS - 30 * ReviewScheduler.ONE_DAY_MS);
        Runner runner = new Runner(state, DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                112.837211, 2.797809, 113);
    }

    @Test
    public void golden_v7_retention85() {
        Runner runner = new Runner(newCard(), 0.85);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 0),
                3.260200, 4.884632, 6);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 3),
                11.326707, 4.868057, 20);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 8),
                31.076364, 4.851596, 56);
    }

    @Test
    public void golden_v8_newCardAgain() {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        assertStep(runner.grade(ReviewScheduler.GRADE_AGAIN, 0),
                0.217200, 7.011400, 0);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 1),
                1.582648, 6.980150, 2);
        assertStep(runner.grade(ReviewScheduler.GRADE_GOOD, 3),
                6.317142, 6.949116, 6);
    }

    @Test
    public void schedule_goodStreak_stabilityStrictlyIncreasing() {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        runner.grade(ReviewScheduler.GRADE_GOOD, 0);
        double previousStability = runner.state.stability;
        for (int i = 0; i < 50; i++) {
            runner.grade(ReviewScheduler.GRADE_GOOD, 1 + (i % 30));
            assertTrue("stability must strictly increase on GOOD, was "
                            + runner.state.stability + " after "
                            + previousStability,
                    runner.state.stability > previousStability);
            previousStability = runner.state.stability;
        }
    }

    @Test
    public void schedule_sameState_easyIntervalAtLeastGoodIntervalAtLeastHardInterval() {
        // from many different memory states, for every rating the interval
        // must be ordered EASY >= GOOD >= HARD
        Random random = new Random(7);
        int[][] gradeSequences = new int[][]{
                {ReviewScheduler.GRADE_GOOD, ReviewScheduler.GRADE_GOOD},
                {ReviewScheduler.GRADE_GOOD, ReviewScheduler.GRADE_EASY},
                {ReviewScheduler.GRADE_EASY, ReviewScheduler.GRADE_GOOD},
                {ReviewScheduler.GRADE_GOOD, ReviewScheduler.GRADE_HARD},
                {ReviewScheduler.GRADE_AGAIN, ReviewScheduler.GRADE_GOOD},
                {ReviewScheduler.GRADE_HARD, ReviewScheduler.GRADE_HARD},
        };
        for (int[] grades : gradeSequences) {
            for (int gap = 0; gap <= 60; gap += 7) {
                long seed = random.nextLong();
                CardReviewState easyState = runSequence(grades, gap, seed);
                CardReviewState goodState = runSequence(grades, gap, seed);
                CardReviewState hardState = runSequence(grades, gap, seed);
                int easyInterval = intervalOf(easyState, ReviewScheduler.GRADE_EASY);
                int goodInterval = intervalOf(goodState, ReviewScheduler.GRADE_GOOD);
                int hardInterval = intervalOf(hardState, ReviewScheduler.GRADE_HARD);
                assertTrue("EASY " + easyInterval + " < GOOD " + goodInterval
                                + " for grades " + grades[0] + "/" + grades[1]
                                + ", gap " + gap,
                        easyInterval >= goodInterval);
                assertTrue("GOOD " + goodInterval + " < HARD " + hardInterval
                                + " for grades " + grades[0] + "/" + grades[1]
                                + ", gap " + gap,
                        goodInterval >= hardInterval);
            }
        }
    }

    /**
     * Builds a non-new memory state by grading a fresh card twice with the
     * given whole-day gap in between (seed only shifts the base time so the
     * states of the same seed are identical)
     */
    private static CardReviewState runSequence(int[] grades, int gap, long seed) {
        Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
        runner.nowMs = BASE_TIME_MS + seed % 1000;
        for (int grade : grades) {
            runner.grade(grade, gap);
        }
        return runner.state;
    }

    private static int intervalOf(CardReviewState state, int grade) {
        Date now = new Date(BASE_TIME_MS);
        Date due = FsrsScheduler.computeDueDate(state, grade, now,
                DEFAULT_RETENTION);
        return (int) Math.round((due.getTime() - now.getTime())
                / (double) ReviewScheduler.ONE_DAY_MS);
    }

    @Test
    public void intervalDays_retention70AtLeastRetention98_forSameStability() {
        Random random = new Random(11);
        for (int i = 0; i < 100; i++) {
            double stability = FsrsParams.STABILITY_MIN
                    + random.nextDouble() * 3650;
            int lowRetention = FsrsScheduler.intervalDays(stability, 0.7);
            int highRetention = FsrsScheduler.intervalDays(stability, 0.98);
            assertTrue("interval at retention 0.7 (" + lowRetention
                            + "d) must be >= interval at retention 0.98 ("
                            + highRetention + "d) for stability " + stability,
                    lowRetention >= highRetention);
        }
    }

    @Test
    public void computeDueDate_again_isAlwaysExactlyNowPlusTenMinutes() {
        Random random = new Random(13);
        for (int i = 0; i < 100; i++) {
            CardReviewState state = runSequence(
                    new int[]{ReviewScheduler.GRADE_GOOD,
                            ReviewScheduler.GRADE_GOOD},
                    random.nextInt(90), i * 17L);
            long nowMs = BASE_TIME_MS + i;
            Date due = FsrsScheduler.computeDueDate(state,
                    ReviewScheduler.GRADE_AGAIN, new Date(nowMs),
                    DEFAULT_RETENTION);
            assertEquals(ReviewScheduler.AGAIN_DUE_DELAY_MS,
                    due.getTime() - nowMs);
        }
    }

    @Test
    public void schedule_randomWalks_stabilityAndDifficultyAlwaysValid() {
        Random random = new Random(42);
        for (int walk = 0; walk < 200; walk++) {
            Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
            for (int step = 0; step < 50; step++) {
                int grade = random.nextInt(4);
                // 0..399 day gap, includes same-day and long-lapse reviews
                runner.grade(grade, random.nextInt(400));
                assertTrue("stability " + runner.state.stability
                                + " must be >= FsrsParams.STABILITY_MIN (walk "
                                + walk + ", step " + step + ")",
                        runner.state.stability >= FsrsParams.STABILITY_MIN);
                assertTrue("difficulty " + runner.state.difficulty
                                + " must be in [1, 10] (walk " + walk
                                + ", step " + step + ")",
                        runner.state.difficulty >= FsrsParams.DIFFICULTY_MIN
                                && runner.state.difficulty
                                <= FsrsParams.DIFFICULTY_MAX);
            }
        }
    }

    @Test
    public void schedule_fsrsSchedulerNeverModifiesSm2Columns() {
        Random random = new Random(17);
        for (int walk = 0; walk < 20; walk++) {
            Runner runner = new Runner(newCard(), DEFAULT_RETENTION);
            CardReviewState state = runner.state;
            // give the SM-2 columns non-default values
            state.intervalDays = 1 + random.nextInt(100);
            state.easeFactor = ReviewScheduler.EASE_MIN
                    + random.nextDouble()
                    * (ReviewScheduler.EASE_MAX - ReviewScheduler.EASE_MIN);
            state.repetitions = random.nextInt(10);
            state.lapses = random.nextInt(10);
            for (int step = 0; step < 30; step++) {
                double intervalDaysBefore = state.intervalDays;
                double easeFactorBefore = state.easeFactor;
                int repetitionsBefore = state.repetitions;
                int lapsesBefore = state.lapses;
                runner.grade(random.nextInt(4), random.nextInt(60));
                assertEquals(intervalDaysBefore, state.intervalDays, 0.0);
                assertEquals(easeFactorBefore, state.easeFactor, 0.0);
                assertEquals(repetitionsBefore, state.repetitions);
                assertEquals(lapsesBefore, state.lapses);
            }
        }
    }

    @Test
    public void schedule_reviewSchedulerNeverModifiesFsrsColumns() {
        // dual-write simulation: whatever SM-2 does, stability/difficulty
        // must stay untouched
        Random random = new Random(19);
        for (int walk = 0; walk < 20; walk++) {
            CardReviewState state = newCard();
            state.stability = 0.1 + random.nextDouble() * 500;
            state.difficulty = 1 + random.nextDouble() * 9;
            long nowMs = BASE_TIME_MS;
            for (int step = 0; step < 30; step++) {
                double stabilityBefore = state.stability;
                double difficultyBefore = state.difficulty;
                nowMs += random.nextInt(60) * ReviewScheduler.ONE_DAY_MS;
                ReviewScheduler.schedule(state, random.nextInt(4),
                        new Date(nowMs));
                assertEquals(stabilityBefore, state.stability, 0.0);
                assertEquals(difficultyBefore, state.difficulty, 0.0);
            }
        }
    }

    @Test
    public void computeDueDate_unseededCard_schedulesFromSm2Interval() {
        // card with SM-2 history but the 0.0 stability sentinel: the due
        // computation must fall back to max(intervalDays, 0.5) without
        // dividing by the zero stability
        CardReviewState state = newCard();
        state.intervalDays = 30;
        Date now = new Date(BASE_TIME_MS);
        Date due = FsrsScheduler.computeDueDate(state,
                ReviewScheduler.GRADE_GOOD, now, DEFAULT_RETENTION);
        int interval = (int) Math.round((due.getTime() - now.getTime())
                / (double) ReviewScheduler.ONE_DAY_MS);
        assertEquals(FsrsScheduler.intervalDays(
                Math.max(30, FsrsParams.SEED_STABILITY_FLOOR),
                DEFAULT_RETENTION), interval);
        // the state is not mutated by the computation
        assertEquals(0.0, state.stability, 0.0);
        assertEquals(0.0, state.difficulty, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void schedule_unknownGrade4_throwsIllegalArgumentException() {
        FsrsScheduler.schedule(newCard(), 4, new Date(BASE_TIME_MS),
                DEFAULT_RETENTION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void schedule_unknownNegativeGrade_throwsIllegalArgumentException() {
        FsrsScheduler.schedule(newCard(), -1, new Date(BASE_TIME_MS),
                DEFAULT_RETENTION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void computeDueDate_unknownGrade_throwsIllegalArgumentException() {
        FsrsScheduler.computeDueDate(newCard(), 99, new Date(BASE_TIME_MS),
                DEFAULT_RETENTION);
    }
}
