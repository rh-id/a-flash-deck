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
import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.Card;

/**
 * Model of the test state
 */
public class TestState implements Serializable {
    private static final long serialVersionUID = 1L;

    // list of choosen cards
    private ArrayList<Card> mChoosenCards;
    // current test index position
    private int mCurrentCardIndex;
    // test id from Test entity
    private long mTestId;
    // answer count per grade, index refers to ReviewScheduler grade constants
    private int[] mGradeCounts = new int[4];
    // session start time in ms
    private long mSessionStartMs;
    // time in ms when the current card was shown
    private long mCardShownAtMs;
    // time in ms when the current card's answer was revealed, 0 = not yet revealed
    private transient long mCardRevealedAtMs;
    // elapsed time in ms actually spent viewing/answering cards
    private long mElapsedMs;

    public TestState(List<Card> choosenCards, long testId) {
        mChoosenCards = new ArrayList<>();
        mChoosenCards.addAll(choosenCards);
        mTestId = testId;
    }

    public Card previousCard() {
        if (mCurrentCardIndex == 0) return null;
        mCurrentCardIndex--;
        return mChoosenCards.get(mCurrentCardIndex);
    }

    public Card currentCard() {
        return mChoosenCards.get(mCurrentCardIndex);
    }

    public Card nextCard() {
        if (mCurrentCardIndex >= mChoosenCards.size() - 1) return null;
        mCurrentCardIndex++;
        return mChoosenCards.get(mCurrentCardIndex);
    }

    public long getTestId() {
        return mTestId;
    }

    public int getCurrentCardIndex() {
        return mCurrentCardIndex;
    }

    public int getTotalCards() {
        return mChoosenCards.size();
    }

    /**
     * Marks the session as started and begins the elapsed time tracking
     */
    public void onSessionStarted() {
        onSessionStarted(System.currentTimeMillis());
    }

    void onSessionStarted(long nowMs) {
        mSessionStartMs = nowMs;
        mCardShownAtMs = nowMs;
    }

    /**
     * Restarts the elapsed time of the current card,
     * must be called by the caller after every card index change
     */
    public void onCardShown() {
        mCardShownAtMs = System.currentTimeMillis();
        mCardRevealedAtMs = 0;
    }

    void onCardShown(long nowMs) {
        mCardShownAtMs = nowMs;
        mCardRevealedAtMs = 0;
    }

    /**
     * Marks the current card's answer as revealed, starts the after-reveal
     * phase of the answer timing
     */
    public void markAnswerRevealed() {
        markAnswerRevealed(System.currentTimeMillis());
    }

    void markAnswerRevealed(long nowMs) {
        mCardRevealedAtMs = nowMs;
    }

    /**
     * Records an answer of the given grade, updates the per-grade counts and
     * accumulates the elapsed time spent on the current card
     *
     * @return the split of the time spent on the current card: before and
     * after the answer was revealed, a grade without a reveal counts the
     * full time as before-reveal
     */
    public AnswerTiming recordAnswer(int grade, long nowMs) {
        long deltaMs = nowMs - mCardShownAtMs;
        long beforeRevealMs = (mCardRevealedAtMs > 0) ?
                mCardRevealedAtMs - mCardShownAtMs : deltaMs;
        long afterRevealMs = (mCardRevealedAtMs > 0) ?
                nowMs - mCardRevealedAtMs : 0;
        if (grade >= 0 && grade < mGradeCounts.length) {
            mGradeCounts[grade]++;
        }
        mElapsedMs += deltaMs;
        mCardShownAtMs = nowMs;
        return new AnswerTiming(beforeRevealMs, afterRevealMs);
    }

    /**
     * Appends the current card to the end of the card list (same instance,
     * so the reversed state carries over), a requeued card is always the
     * last element of the list
     */
    public void requeueCurrentCard() {
        mChoosenCards.add(currentCard());
    }

    public int[] getGradeCounts() {
        return mGradeCounts.clone();
    }

    public long getElapsedMs() {
        return mElapsedMs;
    }

    public long getSessionStartMs() {
        return mSessionStartMs;
    }

    public int getTotalAnswers() {
        int total = 0;
        for (int gradeCount : mGradeCounts) {
            total += gradeCount;
        }
        return total;
    }

    /**
     * The split of the time spent on a card: before and after its answer
     * was revealed
     */
    public static class AnswerTiming {
        public final long beforeRevealMs;
        public final long afterRevealMs;

        public AnswerTiming(long beforeRevealMs, long afterRevealMs) {
            this.beforeRevealMs = beforeRevealMs;
            this.afterRevealMs = afterRevealMs;
        }
    }
}
