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

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.model.McqOptions;

/**
 * Pure generator of multiple-choice options for a test card. Distractor
 * candidates come from the other cards of the same deck within the test
 * session, filtered and de-duplicated so every option text is distinct and
 * never equals the prompt or the correct answer. Previously shown distractor
 * card ids can be excluded (the AGAIN-requeue no-repeat rule), as long as the
 * exclusion keeps at least 2 candidates available.
 *
 * <p>Side resolution mirrors TestPage's question/answer side ternaries: a
 * card's own isReversed flag decides which side is its prompt (shown) and
 * which is its correct answer text, while a distractor candidate's side is
 * chosen by the CURRENT card's reversedness so every option sits on the same
 * side as the correct answer.
 *
 * <p>This is a stateless provider-registered component, obtained via the
 * service locator (see BaseProviderModule); it holds no state and is safe to
 * share app-wide.
 */
public class McqDistractorGenerator {

    public McqDistractorGenerator() {
    }

    /**
     * Generates the multiple-choice options for the current card from the
     * test session's cards.
     *
     * @param current      the card being asked
     * @param sessionCards all cards of the test session, in test order
     *                     (candidates are taken in that order; randomness
     *                     comes from shuffling the option arrangement)
     * @param random       source of randomness for the option shuffle
     * @return the generated options, or null when no options can be built
     * (current card has no id, or fewer than 2 distinct candidates remain)
     */
    public McqOptions generate(Card current, List<Card> sessionCards, Random random) {
        return generate(current, sessionCards, null, random);
    }

    /**
     * Generates the multiple-choice options for the current card from the
     * test session's cards, excluding the given distractor card ids (e.g.
     * the distractors shown on a previous attempt of a re-queued card, so
     * the same set does not immediately repeat).
     *
     * @param current        the card being asked
     * @param sessionCards   all cards of the test session, in test order
     *                       (candidates are taken in that order; randomness
     *                       comes from shuffling the option arrangement)
     * @param excludeCardIds distractor card ids to drop from the candidate
     *                       set, null/empty for none. Ignored when the
     *                       exclusion would leave fewer than 2 distinct
     *                       candidates, so MCQ keeps working in small decks
     *                       instead of collapsing
     * @param random         source of randomness for the option shuffle
     * @return the generated options, or null when no options can be built
     * (current card has no id, or fewer than 2 distinct candidates remain)
     */
    public McqOptions generate(Card current, List<Card> sessionCards,
                               @Nullable List<Long> excludeCardIds, Random random) {
        if (current == null || current.id == null) {
            return null;
        }
        String promptText = trimmed(promptText(current));
        String correctText = trimmed(correctOptionText(current));

        // one candidate per card id (a re-queued card appends the same
        // instance), one candidate text per distinct trimmed text. The
        // distractor side is chosen by the current card's reversedness, so
        // all options sit on the same side as the correct answer
        Set<Long> seenCardIds = new HashSet<>();
        Set<String> seenTexts = new HashSet<>();
        List<String> candidateTexts = new ArrayList<>();
        List<Long> candidateCardIds = new ArrayList<>();
        for (Card candidate : sessionCards) {
            if (candidate == null
                    || candidate.id == null
                    || candidate.id.equals(current.id)
                    || !Objects.equals(candidate.deckId, current.deckId)
                    || !seenCardIds.add(candidate.id)) {
                continue;
            }
            String text = trimmed(matchingSideText(current, candidate));
            if (text.isEmpty()
                    || text.equals(correctText)
                    || text.equals(promptText)
                    || !seenTexts.add(text)) {
                continue;
            }
            candidateTexts.add(text);
            candidateCardIds.add(candidate.id);
        }

        List<String> sourceTexts = candidateTexts;
        List<Long> sourceCardIds = candidateCardIds;
        if (excludeCardIds != null && !excludeCardIds.isEmpty()) {
            List<String> filteredTexts = new ArrayList<>();
            List<Long> filteredCardIds = new ArrayList<>();
            for (int i = 0; i < candidateCardIds.size(); i++) {
                Long candidateId = candidateCardIds.get(i);
                if (!excludeCardIds.contains(candidateId)) {
                    filteredTexts.add(candidateTexts.get(i));
                    filteredCardIds.add(candidateId);
                }
            }
            // only honor the exclusions when at least 2 distinct candidates
            // survive them, otherwise fall back to the unfiltered set so
            // small decks still produce options
            if (filteredCardIds.size() >= 2) {
                sourceTexts = filteredTexts;
                sourceCardIds = filteredCardIds;
            }
        }

        if (sourceTexts.size() < 2) {
            return null;
        }
        int distractorCount = Math.min(3, sourceTexts.size());
        List<String> optionTexts = new ArrayList<>();
        List<Long> distractorCardIds = new ArrayList<>();
        optionTexts.add(correctText);
        for (int i = 0; i < distractorCount; i++) {
            optionTexts.add(sourceTexts.get(i));
            distractorCardIds.add(sourceCardIds.get(i));
        }
        Collections.shuffle(optionTexts, random);
        int correctIndex = optionTexts.indexOf(correctText);
        return new McqOptions(optionTexts, correctIndex, distractorCardIds);
    }

    private static String trimmed(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * @return the prompt text of the card, the side that is shown
     * (reversed card shows its answer side as the question)
     */
    private String promptText(Card card) {
        return card.isReversed ? card.answer : card.question;
    }

    /**
     * @return the correct answer text of the card, the side that is
     * revealed (reversed card's answer is its question side)
     */
    private String correctOptionText(Card card) {
        return card.isReversed ? card.question : card.answer;
    }

    /**
     * @return the distractor text a candidate card contributes for the
     * current card. The side is chosen by the current card's reversedness
     * (so all options sit on the same side as the correct answer); the
     * candidate's own flag is irrelevant here, it only governs the
     * candidate's own prompt/correct when it becomes the current card
     */
    private String matchingSideText(Card current, Card candidate) {
        return current.isReversed ? candidate.question : candidate.answer;
    }
}
