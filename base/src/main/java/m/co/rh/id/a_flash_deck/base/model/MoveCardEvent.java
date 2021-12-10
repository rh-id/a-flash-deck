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

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;

public class MoveCardEvent implements Serializable {
    private Card mMovedCard;
    private Deck mSourceDeck;
    private Deck mDestinationDeck;

    public MoveCardEvent(Card movedCard, Deck sourceDeck, Deck destinationDeck) {
        mMovedCard = movedCard;
        mSourceDeck = sourceDeck;
        mDestinationDeck = destinationDeck;
    }

    public Card getMovedCard() {
        return mMovedCard;
    }

    public Deck getSourceDeck() {
        return mSourceDeck;
    }

    public Deck getDestinationDeck() {
        return mDestinationDeck;
    }
}
