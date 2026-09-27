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
}
