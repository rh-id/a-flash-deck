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

import java.util.Date;

import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;

/**
 * FSRS-6 (Free Spaced Repetition Scheduler), a pure-Java port of the
 * default-parameter scheduler of py-fsrs v6.0.1 (fsrs/fsrs.py, MIT license).
 * Like {@link ReviewScheduler}, it is a static utility that mutates the
 * {@link CardReviewState} entity in place and is a deterministic function of
 * the grade and time history, so it can be advanced on every grade even while
 * another scheduler mode is active (dual-write, see
 * StudyRepository.applyGrade).
 * <p>
 * Column ownership: FsrsScheduler only reads and writes stability,
 * difficulty and last_review_date_time. It never reads or writes the
 * SM-2-lite columns interval_days/ease_factor nor the SM-2-semantics
 * counters repetitions/lapses, and it never writes due_date_time (the active
 * scheduler decides the due date via {@link #computeDueDate}).
 * One documented exception: the one-time seeding path (a card with SM-2-lite
 * history but no FSRS state yet) READS interval_days and ease_factor to
 * derive the initial stability/difficulty (see {@link #schedule}).
 * <p>
 * AGAIN policy: stability and difficulty are still updated on AGAIN
 * (forgetting or short-term formula depending on the elapsed time), but the
 * due date for AGAIN is always now + 10 minutes (same as SM-2-lite, keeps
 * the notification-shade review working).
 */
public final class FsrsScheduler {

    /**
     * DECAY = -w20, FSRS-6 uses -0.2 (FSRS-5 used -0.5)
     */
    private static final double DECAY = -FsrsParams.DEFAULT_WEIGHTS[20];

    /**
     * FACTOR = 0.9^(1/DECAY) - 1, constant that decouples the interval
     * formula from the 0.9 default retention
     */
    private static final double FACTOR = Math.pow(0.9, 1 / DECAY) - 1;

    private FsrsScheduler() {
    }

    /**
     * Mutate the state in place with the FSRS-6 memory update of the given
     * grade. The previous last_review_date_time is captured to compute the
     * elapsed days and is then set to now.
     * A card is new when last_review_date_time is null: stability and
     * difficulty are initialized from the first rating.
     * A card with SM-2-lite history but no FSRS state yet
     * (stability &lt;= 0) is seeded once from its interval_days and
     * ease_factor, then follows the normal elapsed-time update.
     *
     * @param state            review state to update in place
     * @param grade            ReviewScheduler.GRADE_AGAIN .. GRADE_EASY
     * @param now              review time
     * @param desiredRetention target recall probability, clamped via
     *                         FsrsParams.clampDesiredRetention
     */
    public static void schedule(CardReviewState state, int grade, Date now,
                                double desiredRetention) {
        validateGrade(grade);
        Date lastReviewDateTime = state.lastReviewDateTime;
        double stability;
        double difficulty;
        int rating = grade + 1; // AGAIN..EASY -> 1..4
        double retention = FsrsParams.clampDesiredRetention(desiredRetention);
        if (lastReviewDateTime == null) {
            // new card: initialize memory state from the first rating
            stability = initStability(rating);
            difficulty = initDifficulty(rating);
        } else {
            if (state.stability <= 0) {
                // one-time seeding of a card with SM-2-lite history but
                // no FSRS state yet; falls through to the normal update
                stability = seedStability(state);
                difficulty = seedDifficulty(state);
            } else {
                stability = state.stability;
                difficulty = state.difficulty;
            }
            int elapsedDays = elapsedDays(now, lastReviewDateTime);
            if (elapsedDays < 1) {
                // reviewed again on the same day: short-term update
                stability = shortTermStability(stability, rating);
            } else {
                double retrievability = retrievability(elapsedDays, stability);
                stability = nextStability(difficulty, stability,
                        retrievability, rating);
            }
            difficulty = nextDifficulty(difficulty, rating);
        }
        state.stability = stability;
        state.difficulty = difficulty;
        state.lastReviewDateTime = new Date(now.getTime());
    }

    /**
     * @return the due date time this grade would produce with FSRS as the
     * active scheduler: now + 10 minutes for AGAIN, otherwise
     * now + interval(stability, desiredRetention) days. Does not mutate
     * the given state.
     */
    public static Date computeDueDate(CardReviewState state, int grade,
                                      Date now, double desiredRetention) {
        validateGrade(grade);
        if (grade == ReviewScheduler.GRADE_AGAIN) {
            return new Date(now.getTime() + ReviewScheduler.AGAIN_DUE_DELAY_MS);
        }
        double retention = FsrsParams.clampDesiredRetention(desiredRetention);
        double stability = stabilityForComputation(state);
        int intervalDays = intervalDays(stability, retention);
        return new Date(now.getTime()
                + Math.round(intervalDays * (double) ReviewScheduler.ONE_DAY_MS));
    }

    /**
     * @return the FSRS interval in days for the given stability and desired
     * retention, clamped to [1, FsrsParams.MAX_INTERVAL_DAYS]
     */
    public static int intervalDays(double stability, double desiredRetention) {
        double interval = (stability / FACTOR)
                * (Math.pow(desiredRetention, 1 / DECAY) - 1);
        long rounded = Math.round(interval);
        return (int) Math.max(1, Math.min(FsrsParams.MAX_INTERVAL_DAYS, rounded));
    }

    /**
     * @return the stability to schedule with, seeding a card that still has
     * the 0.0 sentinel from its SM-2-lite interval (computation only, does
     * not mutate the state)
     */
    private static double stabilityForComputation(CardReviewState state) {
        if (state.stability > 0) {
            return state.stability;
        }
        return seedStability(state);
    }

    private static void validateGrade(int grade) {
        if (grade < ReviewScheduler.GRADE_AGAIN
                || grade > ReviewScheduler.GRADE_EASY) {
            throw new IllegalArgumentException("Unknown grade: " + grade);
        }
    }

    private static double seedStability(CardReviewState state) {
        return Math.max(state.intervalDays, FsrsParams.SEED_STABILITY_FLOOR);
    }

    private static double seedDifficulty(CardReviewState state) {
        return clampDifficulty(FsrsParams.DIFFICULTY_MAX
                - (state.easeFactor - ReviewScheduler.EASE_MIN) / 1.5 * 9);
    }

    private static int elapsedDays(Date now, Date lastReview) {
        return (int) Math.floor((now.getTime() - lastReview.getTime())
                / (double) ReviewScheduler.ONE_DAY_MS);
    }

    /**
     * Initial stability after the first rating: w[rating-1]
     */
    private static double initStability(int rating) {
        return Math.max(FsrsParams.DEFAULT_WEIGHTS[rating - 1],
                FsrsParams.STABILITY_MIN);
    }

    /**
     * Initial difficulty after the first rating (FSRS-6 exponential form):
     * clamp(w4 - exp(w5 * (rating - 1)) + 1, 1, 10)
     */
    private static double initDifficulty(int rating) {
        return clampDifficulty(FsrsParams.DEFAULT_WEIGHTS[4]
                - Math.exp(FsrsParams.DEFAULT_WEIGHTS[5] * (rating - 1)) + 1);
    }

    /**
     * Recall probability after elapsedDays with the given stability:
     * (1 + FACTOR * elapsedDays / stability)^DECAY
     */
    private static double retrievability(int elapsedDays, double stability) {
        return Math.pow(1 + FACTOR * elapsedDays / stability, DECAY);
    }

    /**
     * Same-day review: stability * exp(w17 * (rating - 3 + w18))
     * * stability^(-w19), with the increment floored at 1 for GOOD and EASY
     */
    private static double shortTermStability(double stability, int rating) {
        double inc = Math.exp(FsrsParams.DEFAULT_WEIGHTS[17]
                * (rating - 3 + FsrsParams.DEFAULT_WEIGHTS[18]))
                * Math.pow(stability, -FsrsParams.DEFAULT_WEIGHTS[19]);
        if (rating == ReviewScheduler.GRADE_GOOD + 1
                || rating == ReviewScheduler.GRADE_EASY + 1) {
            inc = Math.max(inc, 1.0);
        }
        return Math.max(stability * inc, FsrsParams.STABILITY_MIN);
    }

    /**
     * Post-lapse stability (FSRS-6): min of the stability-loss formula and
     * a short-term cap
     */
    private static double forgetStability(double difficulty, double stability,
                                          double retrievability) {
        double stabilityLoss = FsrsParams.DEFAULT_WEIGHTS[11]
                * Math.pow(difficulty, -FsrsParams.DEFAULT_WEIGHTS[12])
                * (Math.pow(stability + 1, FsrsParams.DEFAULT_WEIGHTS[13]) - 1)
                * Math.exp((1 - retrievability)
                * FsrsParams.DEFAULT_WEIGHTS[14]);
        double shortTermCap = stability
                / Math.exp(FsrsParams.DEFAULT_WEIGHTS[17]
                * FsrsParams.DEFAULT_WEIGHTS[18]);
        return Math.min(stabilityLoss, shortTermCap);
    }

    /**
     * Successful-review stability growth (FSRS-6), with HARD penalty and
     * EASY bonus
     */
    private static double recallStability(double difficulty, double stability,
                                          double retrievability, int rating) {
        double hardPenalty =
                rating == ReviewScheduler.GRADE_HARD + 1
                        ? FsrsParams.DEFAULT_WEIGHTS[15] : 1;
        double easyBonus =
                rating == ReviewScheduler.GRADE_EASY + 1
                        ? FsrsParams.DEFAULT_WEIGHTS[16] : 1;
        double increase = Math.exp(FsrsParams.DEFAULT_WEIGHTS[8])
                * (11 - difficulty)
                * Math.pow(stability, -FsrsParams.DEFAULT_WEIGHTS[9])
                * (Math.exp((1 - retrievability)
                * FsrsParams.DEFAULT_WEIGHTS[10]) - 1)
                * hardPenalty * easyBonus;
        return stability * (1 + increase);
    }

    /**
     * Next stability after a non-same-day review, floored at
     * FsrsParams.STABILITY_MIN
     */
    private static double nextStability(double difficulty, double stability,
                                        double retrievability, int rating) {
        double next;
        if (rating == ReviewScheduler.GRADE_AGAIN + 1) {
            next = forgetStability(difficulty, stability, retrievability);
        } else {
            next = recallStability(difficulty, stability, retrievability, rating);
        }
        return Math.max(next, FsrsParams.STABILITY_MIN);
    }

    /**
     * Mean-reverted difficulty update (FSRS-6):
     * clamp(w7 * initDifficulty(4) + (1 - w7) * linear, 1, 10)
     */
    private static double nextDifficulty(double difficulty, int rating) {
        double linear = difficulty + (FsrsParams.DIFFICULTY_MAX - difficulty)
                * (-(FsrsParams.DEFAULT_WEIGHTS[6] * (rating - 3))) / 9.0;
        return clampDifficulty(FsrsParams.DEFAULT_WEIGHTS[7] * initDifficulty(4)
                + (1 - FsrsParams.DEFAULT_WEIGHTS[7]) * linear);
    }

    private static double clampDifficulty(double difficulty) {
        return Math.max(FsrsParams.DIFFICULTY_MIN,
                Math.min(FsrsParams.DIFFICULTY_MAX, difficulty));
    }
}
