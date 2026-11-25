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

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    // active test mode, TestMode.SELF_GRADE or TestMode.MULTIPLE_CHOICE
    private int mTestMode = TestMode.SELF_GRADE;
    // generated multiple-choice options per card id, they are serialized
    // with the state so a resumed test shows the exact same options the
    // card was first shown with (stable across navigation and app restart)
    private HashMap<Long, McqOptions> mMcqOptionsByCardId = new HashMap<>();
    // distractor card ids to exclude when the options of a card are
    // regenerated (stashed on a AGAIN requeue, consumed on the re-show),
    // also serialized for resume safety
    private HashMap<Long, ArrayList<Long>> mPendingDistractorExclusions = new HashMap<>();

    public TestState(List<Card> choosenCards, long testId) {
        mChoosenCards = new ArrayList<>();
        mChoosenCards.addAll(choosenCards);
        mTestId = testId;
    }

    private void readObject(ObjectInputStream objectInputStream)
            throws IOException, ClassNotFoundException {
        objectInputStream.defaultReadObject();
        // test states serialized by older app versions carry no values for
        // the MCQ fields (mTestMode deserializes as 0 == SELF_GRADE, the
        // maps are absent), initialize the maps so resuming such a state
        // never runs into a NullPointerException
        if (mMcqOptionsByCardId == null) {
            mMcqOptionsByCardId = new HashMap<>();
        }
        if (mPendingDistractorExclusions == null) {
            mPendingDistractorExclusions = new HashMap<>();
        }
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
     * Returns an unmodifiable view of the test's cards
     */
    public List<Card> getCards() {
        return Collections.unmodifiableList(mChoosenCards);
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

    public int getTestMode() {
        return mTestMode;
    }

    public void setTestMode(int testMode) {
        mTestMode = TestMode.normalize(testMode);
    }

    /**
     * @return the generated multiple-choice options of the given card,
     * null when none were generated. These survive serialization so a
     * resumed test re-shows the exact same options (resume-safe)
     */
    @Nullable
    public McqOptions getMcqOptions(long cardId) {
        return mMcqOptionsByCardId.get(cardId);
    }

    /**
     * Stores the generated multiple-choice options of the given card,
     * they persist with the serialized state until removed
     */
    public void putMcqOptions(long cardId, McqOptions mcqOptions) {
        mMcqOptionsByCardId.put(cardId, mcqOptions);
    }

    /**
     * Removes the stored multiple-choice options of the given card,
     * e.g. so a re-queued card regenerates a fresh set
     */
    public void removeMcqOptions(long cardId) {
        mMcqOptionsByCardId.remove(cardId);
    }

    /**
     * Stashes distractor card ids to exclude when the options of the
     * given card are regenerated, they survive serialization so the
     * exclusion also holds across an app restart before the re-show
     */
    public void stashPendingExclusions(long cardId, List<Long> distractorCardIds) {
        mPendingDistractorExclusions.put(cardId, new ArrayList<>(distractorCardIds));
    }

    /**
     * Reads the pending distractor exclusions of the given card without
     * removing them, e.g. to run a generation attempt against the stashed
     * ids before they are consumed
     *
     * @return the stashed ids, or null when none were stashed
     */
    @Nullable
    public List<Long> peekPendingExclusions(long cardId) {
        return mPendingDistractorExclusions.get(cardId);
    }

    /**
     * Consumes the pending distractor exclusions of the given card.
     *
     * @return the stashed ids, or null when none were stashed; the entry
     * is removed either way so exclusions apply exactly once
     */
    @Nullable
    public List<Long> consumePendingExclusions(long cardId) {
        return mPendingDistractorExclusions.remove(cardId);
    }

    /**
     * The split of the time spent on a card: before and after its answer
     * was revealed
     */
    public static class AnswerTiming implements Serializable {
        private static final long serialVersionUID = 1L;

        public final long beforeRevealMs;
        public final long afterRevealMs;

        public AnswerTiming(long beforeRevealMs, long afterRevealMs) {
            this.beforeRevealMs = beforeRevealMs;
            this.afterRevealMs = afterRevealMs;
        }
    }
}
