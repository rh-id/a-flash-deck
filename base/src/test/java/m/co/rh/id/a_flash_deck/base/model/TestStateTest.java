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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import m.co.rh.id.a_flash_deck.base.entity.Card;

public class TestStateTest {

    private static final long BASE_TIME_MS = 1_700_000_000_000L;

    private static Card newCard(long id) {
        Card card = new Card();
        card.id = id;
        card.question = "Question " + id;
        card.answer = "Answer " + id;
        return card;
    }

    private static TestState newTestState(Card... cards) {
        return new TestState(Arrays.asList(cards), 1L);
    }

    @Test
    public void recordAnswer_incrementsGradeCountsAndAccumulatesElapsed() {
        TestState testState = newTestState(newCard(1));
        testState.onSessionStarted(BASE_TIME_MS);
        testState.recordAnswer(ReviewScheduler.GRADE_GOOD, BASE_TIME_MS + 5000);
        testState.recordAnswer(ReviewScheduler.GRADE_EASY, BASE_TIME_MS + 9000);
        int[] gradeCounts = testState.getGradeCounts();
        assertEquals(1, gradeCounts[ReviewScheduler.GRADE_GOOD]);
        assertEquals(1, gradeCounts[ReviewScheduler.GRADE_EASY]);
        assertEquals(0, gradeCounts[ReviewScheduler.GRADE_AGAIN]);
        assertEquals(2, testState.getTotalAnswers());
        assertEquals(9000, testState.getElapsedMs());
    }

    @Test
    public void recordAnswer_unknownGrade_doesNotThrowAndDoesNotCount() {
        TestState testState = newTestState(newCard(1));
        testState.onSessionStarted(BASE_TIME_MS);
        testState.recordAnswer(99, BASE_TIME_MS + 1000);
        assertEquals(0, testState.getTotalAnswers());
        assertEquals(1000, testState.getElapsedMs());
    }

    @Test
    public void recordAnswer_withReveal_splitsBeforeAndAfterReveal() {
        TestState testState = newTestState(newCard(1));
        testState.onSessionStarted(BASE_TIME_MS);
        testState.markAnswerRevealed(BASE_TIME_MS + 2000);
        TestState.AnswerTiming timing = testState.recordAnswer(
                ReviewScheduler.GRADE_GOOD, BASE_TIME_MS + 5000);
        assertEquals(2000, timing.beforeRevealMs);
        assertEquals(3000, timing.afterRevealMs);
        assertEquals(5000, testState.getElapsedMs());
    }

    @Test
    public void recordAnswer_withoutReveal_countsFullDeltaAsBeforeReveal() {
        TestState testState = newTestState(newCard(1));
        testState.onSessionStarted(BASE_TIME_MS);
        TestState.AnswerTiming timing = testState.recordAnswer(
                ReviewScheduler.GRADE_GOOD, BASE_TIME_MS + 4000);
        assertEquals(4000, timing.beforeRevealMs);
        assertEquals(0, timing.afterRevealMs);
    }

    @Test
    public void onCardShown_resetsAnswerRevealed() {
        Card card1 = newCard(1);
        Card card2 = newCard(2);
        TestState testState = newTestState(card1, card2);
        testState.onSessionStarted(BASE_TIME_MS);
        testState.markAnswerRevealed(BASE_TIME_MS + 1000);
        testState.onCardShown(BASE_TIME_MS + 2000);
        TestState.AnswerTiming timing = testState.recordAnswer(
                ReviewScheduler.GRADE_GOOD, BASE_TIME_MS + 5000);
        assertEquals(3000, timing.beforeRevealMs);
        assertEquals(0, timing.afterRevealMs);
    }

    @Test
    public void getGradeCounts_returnsClone() {
        TestState testState = newTestState(newCard(1));
        testState.onSessionStarted(BASE_TIME_MS);
        testState.recordAnswer(ReviewScheduler.GRADE_GOOD, BASE_TIME_MS + 1000);
        int[] gradeCounts = testState.getGradeCounts();
        gradeCounts[ReviewScheduler.GRADE_GOOD] = 99;
        assertEquals(1, testState.getGradeCounts()[ReviewScheduler.GRADE_GOOD]);
    }

    @Test
    public void requeueCurrentCard_appendsSameInstanceAndIncrementsTotal() {
        Card card1 = newCard(1);
        Card card2 = newCard(2);
        TestState testState = newTestState(card1, card2);
        testState.requeueCurrentCard();
        assertEquals(3, testState.getTotalCards());
        assertSame(card1, testState.currentCard());
        testState.nextCard();
        assertSame(card2, testState.currentCard());
        testState.nextCard();
        assertSame(card1, testState.currentCard());
    }

    @Test
    public void serialization_roundTrip_preservesState() throws Exception {
        Card card1 = newCard(1);
        Card card2 = newCard(2);
        TestState testState = newTestState(card1, card2);
        testState.onSessionStarted(BASE_TIME_MS);
        testState.recordAnswer(ReviewScheduler.GRADE_GOOD, BASE_TIME_MS + 5000);
        testState.requeueCurrentCard();
        testState.nextCard();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(testState);
        }
        TestState deserialized;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            deserialized = (TestState) objectInputStream.readObject();
        }

        assertEquals(1L, deserialized.getTestId());
        assertEquals(1, deserialized.getCurrentCardIndex());
        assertEquals(3, deserialized.getTotalCards());
        assertEquals(1, deserialized.getGradeCounts()[ReviewScheduler.GRADE_GOOD]);
        assertEquals(1, deserialized.getTotalAnswers());
        assertEquals(5000, deserialized.getElapsedMs());
    }

    @Test
    public void mcqDefaults_afterConstruction() {
        TestState testState = newTestState(newCard(1));
        assertEquals(TestMode.SELF_GRADE, testState.getTestMode());
        assertNull(testState.getMcqOptions(1L));
        assertNull(testState.consumePendingExclusions(1L));
    }

    @Test
    public void setTestMode_normalizesUnknownValues() {
        TestState testState = newTestState(newCard(1));
        testState.setTestMode(TestMode.MULTIPLE_CHOICE);
        assertEquals(TestMode.MULTIPLE_CHOICE, testState.getTestMode());
        testState.setTestMode(99);
        assertEquals(TestMode.SELF_GRADE, testState.getTestMode());
    }

    @Test
    public void mcqState_roundTripsThroughSerialization() throws Exception {
        TestState testState = newTestState(newCard(1), newCard(2), newCard(3), newCard(4));
        testState.setTestMode(TestMode.MULTIPLE_CHOICE);
        McqOptions options = new McqOptions(
                Arrays.asList("Correct", "D2", "D3", "D4"), 0,
                Arrays.asList(2L, 3L, 4L));
        testState.putMcqOptions(1L, options);
        testState.stashPendingExclusions(1L, Arrays.asList(2L, 3L, 4L));

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(testState);
        }
        TestState deserialized;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            deserialized = (TestState) objectInputStream.readObject();
        }

        assertEquals(TestMode.MULTIPLE_CHOICE, deserialized.getTestMode());
        McqOptions deserializedOptions = deserialized.getMcqOptions(1L);
        assertNotNull(deserializedOptions);
        assertEquals(options.getOptionTexts(), deserializedOptions.getOptionTexts());
        assertEquals(options.getCorrectIndex(), deserializedOptions.getCorrectIndex());
        assertEquals(options.getDistractorCardIds(), deserializedOptions.getDistractorCardIds());
        assertEquals(Arrays.asList(2L, 3L, 4L),
                deserialized.consumePendingExclusions(1L));
        assertNull(deserialized.consumePendingExclusions(1L));
    }

    @Test
    public void consumePendingExclusions_returnsStashedIdsOnceThenNull() {
        TestState testState = newTestState(newCard(1));
        assertNull(testState.consumePendingExclusions(1L));
        testState.stashPendingExclusions(1L, Arrays.asList(2L, 3L));
        assertEquals(Arrays.asList(2L, 3L), testState.consumePendingExclusions(1L));
        assertNull(testState.consumePendingExclusions(1L));
        // stashing again after consumption works independently
        testState.stashPendingExclusions(1L, Arrays.asList(4L));
        assertEquals(Arrays.asList(4L), testState.consumePendingExclusions(1L));
    }

    @Test
    public void peekPendingExclusions_readsWithoutRemoving() {
        TestState testState = newTestState(newCard(1));
        assertNull(testState.peekPendingExclusions(1L));
        testState.stashPendingExclusions(1L, Arrays.asList(2L, 3L));
        // peeking is non-destructive: repeated peeks are stable
        assertEquals(Arrays.asList(2L, 3L), testState.peekPendingExclusions(1L));
        assertEquals(Arrays.asList(2L, 3L), testState.peekPendingExclusions(1L));
        // consume still removes the entry exactly once afterwards
        assertEquals(Arrays.asList(2L, 3L), testState.consumePendingExclusions(1L));
        assertNull(testState.peekPendingExclusions(1L));
        assertNull(testState.consumePendingExclusions(1L));
    }

    @Test
    public void removeMcqOptions_dropsStoredOptions() {
        TestState testState = newTestState(newCard(1));
        testState.putMcqOptions(1L, new McqOptions(
                Arrays.asList("Correct", "D2", "D3"), 0, Arrays.asList(2L, 3L)));
        assertNotNull(testState.getMcqOptions(1L));
        testState.removeMcqOptions(1L);
        assertNull(testState.getMcqOptions(1L));
    }
}
