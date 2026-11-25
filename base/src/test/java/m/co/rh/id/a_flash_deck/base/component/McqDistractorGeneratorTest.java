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

package m.co.rh.id.a_flash_deck.base.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.model.McqOptions;
import m.co.rh.id.a_flash_deck.base.model.TestState;

public class McqDistractorGeneratorTest {

    private static final Random RANDOM = new Random(1234L);

    private final McqDistractorGenerator mGenerator = new McqDistractorGenerator();

    private static Card newCard(long id, String question, String answer) {
        Card card = new Card();
        card.id = id;
        card.deckId = 1L;
        card.question = question;
        card.answer = answer;
        return card;
    }

    @Test
    public void generate_fourOptions_withAtLeastThreeDistinctCandidates() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(4, options.getOptionTexts().size());
        assertEquals(3, options.getDistractorCardIds().size());
    }

    @Test
    public void generate_threeOptions_withExactlyTwoCandidates() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(3, options.getOptionTexts().size());
        assertEquals(2, options.getDistractorCardIds().size());
    }

    @Test
    public void generate_null_withFewerThanTwoCandidates() {
        Card current = newCard(1L, "Prompt", "Correct");
        assertNull(mGenerator.generate(current,
                Collections.singletonList(current), RANDOM));
        assertNull(mGenerator.generate(current,
                Arrays.asList(current, newCard(2L, "Q2", "D2")), RANDOM));
    }

    @Test
    public void generate_null_whenCurrentCardHasNoId() {
        Card current = newCard(1L, "Prompt", "Correct");
        current.id = null;
        assertNull(mGenerator.generate(current,
                Arrays.asList(current, newCard(2L, "Q2", "D2"),
                        newCard(3L, "Q3", "D3")), RANDOM));
    }

    @Test
    public void generate_duplicateTextsCollapse() {
        Card current = newCard(1L, "Prompt", "Correct");
        // two cards contributing the same trimmed distractor text count once
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "  D2  "),
                newCard(4L, "Q4", "D4"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(3, options.getOptionTexts().size());
        List<String> texts = options.getOptionTexts();
        long distinct = new ArrayList<>(texts).stream().distinct().count();
        assertEquals(texts.size(), distinct);
    }

    @Test
    public void generate_correctTextCandidatesDropped() {
        Card current = newCard(1L, "Prompt", "Correct");
        // candidates equal to the correct text (even with padding) are dropped
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "Correct"),
                newCard(3L, "Q3", " Correct "),
                newCard(4L, "Q4", "D4"),
                newCard(5L, "Q5", "D5"),
                newCard(6L, "Q6", "D6"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertTrue(options.getOptionTexts().stream()
                .filter("Correct"::equals).count() == 1);
    }

    @Test
    public void generate_promptEqualCandidatesDropped() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "Prompt"),
                newCard(3L, "Q3", " Prompt "),
                newCard(4L, "Q4", "D4"),
                newCard(5L, "Q5", "D5"),
                newCard(6L, "Q6", "D6"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("Prompt"::equals));
    }

    @Test
    public void generate_differentDeckCardsExcluded() {
        Card current = newCard(1L, "Prompt", "Correct");
        Card otherDeck1 = newCard(2L, "Q2", "D2");
        otherDeck1.deckId = 2L;
        Card otherDeck2 = newCard(3L, "Q3", "D3");
        otherDeck2.deckId = 2L;
        List<Card> sessionCards = Arrays.asList(current, otherDeck1, otherDeck2);
        assertNull(mGenerator.generate(current, sessionCards, RANDOM));
    }

    @Test
    public void generate_emptySideTextCardsExcluded() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", ""),
                newCard(3L, "Q3", "   "),
                newCard(4L, "Q4", null),
                newCard(5L, "Q5", "D5"),
                newCard(6L, "Q6", "D6"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(3, options.getOptionTexts().size());
        assertEquals(Arrays.asList(5L, 6L), options.getDistractorCardIds());
    }

    @Test
    public void generate_shuffledOptionsContainCorrectTextOnceAndIndexPointsToIt() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"));
        for (int i = 0; i < 50; i++) {
            McqOptions options = mGenerator.generate(
                    current, sessionCards, new Random(i));
            assertNotNull(options);
            List<String> texts = options.getOptionTexts();
            assertEquals(1, texts.stream().filter("Correct"::equals).count());
            assertEquals("Correct", texts.get(options.getCorrectIndex()));
        }
    }

    @Test
    public void generate_reversedCardResolvesSides() {
        Card current = newCard(1L, "Prompt", "Correct");
        current.isReversed = true;
        // reversed card: prompt is the answer side ("Correct"), correct
        // option text is the question side ("Prompt"); distractors follow
        // the current card's side, so candidates contribute their questions
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(4, options.getOptionTexts().size());
        assertEquals("Prompt", options.getOptionTexts().get(options.getCorrectIndex()));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("Correct"::equals));
        // distractors are the candidates' question texts, never their answers
        assertTrue(options.getOptionTexts().contains("Q2"));
        assertTrue(options.getOptionTexts().contains("Q3"));
        assertTrue(options.getOptionTexts().contains("Q4"));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("D2"::equals));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("D3"::equals));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("D4"::equals));
        assertEquals(Arrays.asList(2L, 3L, 4L), options.getDistractorCardIds());
    }

    @Test
    public void generate_candidateSideFollowsCurrentCard() {
        Card current = newCard(1L, "Prompt", "Correct");
        Card candidate = newCard(2L, "ReversedCandidateQuestion", "ReversedCandidateAnswer");
        candidate.isReversed = true;
        // the candidate's own isReversed flag is irrelevant for distractors:
        // under a non-reversed current it contributes its ANSWER side
        List<Card> sessionCards = Arrays.asList(
                current,
                candidate,
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(4, options.getOptionTexts().size());
        assertTrue(options.getOptionTexts().stream()
                .anyMatch("ReversedCandidateAnswer"::equals));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("ReversedCandidateQuestion"::equals));
        assertEquals(Arrays.asList(2L, 3L, 4L), options.getDistractorCardIds());
    }

    @Test
    public void generate_distractorsFollowReversedCurrentSide() {
        Card current = newCard(1L, "Prompt", "Correct");
        current.isReversed = true;
        // regression: a reversed current makes every candidate contribute
        // its question side, so distractors are questions and never answers
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Question A", "Answer A"),
                newCard(3L, "Question B", "Answer B"),
                newCard(4L, "Question C", "Answer C"),
                newCard(5L, "Question D", "Answer D"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(4, options.getOptionTexts().size());
        assertTrue(options.getOptionTexts().stream()
                .noneMatch(text -> text.startsWith("Answer")));
        for (String question : Arrays.asList("Question A", "Question B", "Question C")) {
            assertTrue(options.getOptionTexts().contains(question));
        }
        assertEquals(Arrays.asList(2L, 3L, 4L), options.getDistractorCardIds());
    }

    @Test
    public void generate_mixedFlags_underReversedCurrent_allFromQuestionSide() {
        Card current = newCard(1L, "Prompt", "Correct");
        current.isReversed = true;
        Card reversedCandidate = newCard(2L, "Reversed Q", "Reversed A");
        reversedCandidate.isReversed = true;
        // one reversed + two normal candidates under a reversed current:
        // all distractors come from the question side regardless of the
        // candidates' own flags
        List<Card> sessionCards = Arrays.asList(
                current,
                reversedCandidate,
                newCard(3L, "Q3", "A3"),
                newCard(4L, "Q4", "A4"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(4, options.getOptionTexts().size());
        assertTrue(options.getOptionTexts().contains("Reversed Q"));
        assertTrue(options.getOptionTexts().contains("Q3"));
        assertTrue(options.getOptionTexts().contains("Q4"));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("Reversed A"::equals));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("A3"::equals));
        assertTrue(options.getOptionTexts().stream()
                .noneMatch("A4"::equals));
        assertEquals(Arrays.asList(2L, 3L, 4L), options.getDistractorCardIds());
    }

    @Test
    public void generate_sideRelativeEligibility() {
        // candidate with an empty question and a non-empty answer
        Card emptyQuestionCandidate = newCard(2L, "", "D2");
        Card otherCandidate = newCard(3L, "Q3", "D3");
        Card anotherCandidate = newCard(4L, "Q4", "D4");

        // reversed current reads the candidate's question side: the
        // empty-question candidate is excluded
        Card reversedCurrent = newCard(1L, "Prompt", "Correct");
        reversedCurrent.isReversed = true;
        McqOptions reversedOptions = mGenerator.generate(reversedCurrent,
                Arrays.asList(reversedCurrent, emptyQuestionCandidate,
                        otherCandidate, anotherCandidate), RANDOM);
        assertNotNull(reversedOptions);
        assertEquals(3, reversedOptions.getOptionTexts().size());
        assertTrue(reversedOptions.getOptionTexts().stream()
                .noneMatch("D2"::equals));
        assertEquals(Arrays.asList(3L, 4L), reversedOptions.getDistractorCardIds());

        // normal current reads the candidate's answer side: the same
        // candidate is eligible
        Card normalCurrent = newCard(1L, "Prompt", "Correct");
        McqOptions normalOptions = mGenerator.generate(normalCurrent,
                Arrays.asList(normalCurrent, emptyQuestionCandidate,
                        otherCandidate, anotherCandidate), RANDOM);
        assertNotNull(normalOptions);
        assertEquals(4, normalOptions.getOptionTexts().size());
        assertTrue(normalOptions.getOptionTexts().stream()
                .anyMatch("D2"::equals));
        assertEquals(Arrays.asList(2L, 3L, 4L), normalOptions.getDistractorCardIds());
    }

    @Test
    public void generate_requeuedDuplicateInstanceDedupedById() {
        Card current = newCard(1L, "Prompt", "Correct");
        Card candidate = newCard(2L, "Q2", "D2");
        // requeue duplicates the same instance: dedupe by id keeps one candidate
        List<Card> sessionCards = Arrays.asList(current, candidate, candidate);
        assertNull(mGenerator.generate(current, sessionCards, RANDOM));

        Card candidate2 = newCard(3L, "Q3", "D3");
        List<Card> sessionCards2 = Arrays.asList(current, candidate, candidate, candidate2);
        McqOptions options = mGenerator.generate(current, sessionCards2, RANDOM);
        assertNotNull(options);
        assertEquals(3, options.getOptionTexts().size());
        assertEquals(2, options.getDistractorCardIds().size());
    }

    @Test
    public void generate_excludesPreviousDistractorIds_whenAlternativesExist() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"),
                newCard(5L, "Q5", "D5"),
                newCard(6L, "Q6", "D6"));
        // first round as during the initial card show
        McqOptions first = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(first);
        assertEquals(Arrays.asList(2L, 3L, 4L), first.getDistractorCardIds());
        // regeneration on the AGAIN-requeue re-show excludes the shown distractors
        McqOptions second = mGenerator.generate(current, sessionCards,
                first.getDistractorCardIds(), RANDOM);
        assertNotNull(second);
        assertEquals(3, second.getOptionTexts().size());
        assertTrue(Collections.disjoint(second.getDistractorCardIds(),
                first.getDistractorCardIds()));
        assertEquals(Arrays.asList(5L, 6L), second.getDistractorCardIds());
        assertEquals("Correct", second.getOptionTexts().get(second.getCorrectIndex()));
    }

    @Test
    public void generate_ignoresExclusions_whenPoolWouldFallBelowTwoCandidates() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"));
        // excluding both candidates would leave 1, so the exclusions are
        // ignored and the unfiltered set is used
        McqOptions options = mGenerator.generate(current, sessionCards,
                Arrays.asList(2L, 3L), RANDOM);
        assertNotNull(options);
        assertEquals(3, options.getOptionTexts().size());
        assertEquals(Arrays.asList(2L, 3L), options.getDistractorCardIds());
    }

    @Test
    public void generate_honorsExclusions_atTheTwoCandidateBoundary() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"));
        // excluding one of three candidates leaves exactly 2, exclusions honored
        McqOptions options = mGenerator.generate(current, sessionCards,
                Collections.singletonList(2L), RANDOM);
        assertNotNull(options);
        assertEquals(3, options.getOptionTexts().size());
        assertEquals(Arrays.asList(3L, 4L), options.getDistractorCardIds());
    }

    @Test
    public void generate_fourArgOverload_nullExclusionsBehavesLikeUnfiltered() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"));
        McqOptions options = mGenerator.generate(
                current, sessionCards, null, RANDOM);
        assertNotNull(options);
        assertEquals(4, options.getOptionTexts().size());
        assertEquals(Arrays.asList(2L, 3L, 4L), options.getDistractorCardIds());
    }

    @Test
    public void distractorCardIdsRecorded_andConsumedOnceViaPendingExclusions() {
        Card current = newCard(1L, "Prompt", "Correct");
        List<Card> sessionCards = Arrays.asList(
                current,
                newCard(2L, "Q2", "D2"),
                newCard(3L, "Q3", "D3"),
                newCard(4L, "Q4", "D4"));
        McqOptions options = mGenerator.generate(current, sessionCards, RANDOM);
        assertNotNull(options);
        assertEquals(Arrays.asList(2L, 3L, 4L), options.getDistractorCardIds());

        // TestState-level exclusion round trip as performed on an AGAIN requeue
        TestState testState = new TestState(new ArrayList<>(sessionCards), 1L);
        testState.stashPendingExclusions(current.id, options.getDistractorCardIds());
        assertEquals(options.getDistractorCardIds(),
                testState.consumePendingExclusions(current.id));
        assertNull(testState.consumePendingExclusions(current.id));
    }
}
