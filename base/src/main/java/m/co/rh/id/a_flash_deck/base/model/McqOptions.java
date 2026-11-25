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

/**
 * Generated multiple-choice options for a single test card: the shuffled
 * option texts, the index of the correct option and the card ids of the
 * chosen distractors (used to avoid repeating the same distractors after
 * a card is re-queued).
 */
public class McqOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    private ArrayList<String> mOptionTexts;
    private int mCorrectIndex;
    private ArrayList<Long> mDistractorCardIds;

    public McqOptions(List<String> optionTexts, int correctIndex, List<Long> distractorCardIds) {
        mOptionTexts = new ArrayList<>(optionTexts);
        mCorrectIndex = correctIndex;
        mDistractorCardIds = new ArrayList<>(distractorCardIds);
    }

    /**
     * @return defensive copy of the shuffled option texts
     */
    public ArrayList<String> getOptionTexts() {
        return new ArrayList<>(mOptionTexts);
    }

    public int getCorrectIndex() {
        return mCorrectIndex;
    }

    /**
     * @return defensive copy of the card ids of the chosen distractors
     */
    public ArrayList<Long> getDistractorCardIds() {
        return new ArrayList<>(mDistractorCardIds);
    }
}
