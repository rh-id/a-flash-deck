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

import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.Card;

/**
 * A deck to import together with the guid of the Anki note each card was
 * imported from. {@link #cardGuids} is aligned with
 * {@link DeckModel#getCardList()}; entries may be null or empty when a card
 * has no source note guid (e.g. native app format imports).
 */
public class DeckImportPayload {
    private final DeckModel mDeckModel;
    private final List<String> mCardGuids;

    public DeckImportPayload(DeckModel deckModel, List<String> cardGuids) {
        mDeckModel = deckModel;
        mCardGuids = cardGuids == null ? Collections.emptyList() : cardGuids;
    }

    public DeckModel getDeckModel() {
        return mDeckModel;
    }

    /**
     * Anki note guid per card, aligned with the deck model card list.
     */
    public List<String> getCardGuids() {
        return mCardGuids;
    }

    /**
     * Returns the guid of the card at the given list position, or null when
     * the payload has no guid for it.
     */
    public String getCardGuid(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= mCardGuids.size()) {
            return null;
        }
        return mCardGuids.get(cardIndex);
    }

    /**
     * Returns true when the given guid is usable for duplicate detection.
     */
    public static boolean isValidGuid(String guid) {
        return guid != null && !guid.isEmpty();
    }

    /**
     * Convenience: the card at the given list position.
     */
    public Card getCard(int cardIndex) {
        return mDeckModel.getCardList().get(cardIndex);
    }
}
