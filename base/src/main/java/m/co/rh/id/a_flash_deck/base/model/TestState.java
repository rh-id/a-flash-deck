/*
 *     Copyright (C) 2021 Ruby Hartono
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
import m.co.rh.id.a_flash_deck.base.entity.Deck;

/**
 * Model of the test state
 */
public class TestState implements Serializable {
    // list of choosen decks
    private ArrayList<Deck> mChoosenDecks;
    // list of choosen cards
    private ArrayList<Card> mChoosenCards;
    // current test index position
    private int mCurrentCardIndex;
    // test id from Test entity
    private long mTestId;

    public TestState(List<Deck> choosenDecks, List<Card> choosenCards, long testId) {
        mChoosenDecks = new ArrayList<>();
        mChoosenDecks.addAll(choosenDecks);
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
        if (mCurrentCardIndex == mChoosenCards.size()) return null;
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
}
