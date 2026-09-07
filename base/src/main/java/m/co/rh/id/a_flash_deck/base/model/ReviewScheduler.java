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
 * SM-2-lite spaced repetition scheduler,
 * computes the next review state of a card based on the given grade
 */
public final class ReviewScheduler {
    public static final int GRADE_AGAIN = 0;
    public static final int GRADE_HARD = 1;
    public static final int GRADE_GOOD = 2;
    public static final int GRADE_EASY = 3;
    public static final double EASE_INIT = 2.5;
    public static final double EASE_MIN = 1.3;
    public static final double EASE_MAX = 2.8;
    public static final int INTERVAL_CAP_DAYS = 365;
    public static final long AGAIN_DUE_DELAY_MS = 10 * 60 * 1000L;
    public static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;

    private ReviewScheduler() {
    }

    /**
     * Review state of a never-studied card,
     * dueDateTime and lastReviewDateTime are left null until first schedule
     */
    public static CardReviewState newCardReviewState(long cardId) {
        CardReviewState cardReviewState = new CardReviewState();
        cardReviewState.cardId = cardId;
        cardReviewState.intervalDays = 0;
        cardReviewState.easeFactor = EASE_INIT;
        cardReviewState.repetitions = 0;
        cardReviewState.lapses = 0;
        return cardReviewState;
    }

    /**
     * Mutate the state in place with the review result of the given grade.
     * A card is new when lastReviewDateTime is null or repetitions is 0
     * (never-studied card or first review after a lapse).
     * AGAIN: fail the card, reset repetitions, count a lapse, due in 10 minutes.
     * HARD: small interval growth, ease penalty.
     * GOOD: interval grows by ease factor (1 day for new card).
     * EASY: fastest interval growth (4 days for new card), ease bonus.
     * Interval is capped at 365 days when computing the due date time.
     */
    public static void schedule(CardReviewState state, int grade, Date now) {
        boolean isNew = state.lastReviewDateTime == null || state.repetitions == 0;
        switch (grade) {
            case GRADE_AGAIN:
                state.repetitions = 0;
                state.lapses++;
                state.intervalDays = 0;
                state.easeFactor = clampEase(state.easeFactor - 0.20);
                state.dueDateTime = new Date(now.getTime() + AGAIN_DUE_DELAY_MS);
                break;
            case GRADE_HARD:
                state.repetitions++;
                state.intervalDays = Math.max(1, state.intervalDays * 1.2);
                state.easeFactor = clampEase(state.easeFactor - 0.15);
                state.dueDateTime = dueFromInterval(state, now);
                break;
            case GRADE_GOOD:
                state.repetitions++;
                state.intervalDays = isNew ? 1 : state.intervalDays * state.easeFactor;
                state.dueDateTime = dueFromInterval(state, now);
                break;
            case GRADE_EASY:
                state.repetitions++;
                state.intervalDays = isNew ? 4 : state.intervalDays * state.easeFactor * 1.3;
                state.easeFactor = clampEase(state.easeFactor + 0.15);
                state.dueDateTime = dueFromInterval(state, now);
                break;
            default:
                throw new IllegalArgumentException("Unknown grade: " + grade);
        }
        state.lastReviewDateTime = new Date(now.getTime());
    }

    private static double clampEase(double ease) {
        return Math.max(EASE_MIN, Math.min(EASE_MAX, ease));
    }

    private static Date dueFromInterval(CardReviewState state, Date now) {
        double cappedIntervalDays = Math.min(state.intervalDays, INTERVAL_CAP_DAYS);
        return new Date(now.getTime() + Math.round(cappedIntervalDays * ONE_DAY_MS));
    }
}
