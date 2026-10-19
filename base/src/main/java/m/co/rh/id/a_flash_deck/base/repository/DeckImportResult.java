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

package m.co.rh.id.a_flash_deck.base.repository;

import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.model.DeckModel;

/**
 * Outcome of a guid-aware deck import: the decks and cards that were actually
 * created (deck and card ids are set on the returned models) and how many
 * cards were skipped because their Anki note guid was already imported.
 */
public class DeckImportResult {

    /**
     * Number of decks that were created (empty list when all cards were
     * skipped as duplicates).
     */
    public final int decksCreated;
    /**
     * Number of cards that were inserted.
     */
    public final int cardsImported;
    /**
     * Number of cards skipped because their note guid was already imported
     * (or repeated within the same import).
     */
    public final int duplicateCardsSkipped;
    /**
     * Deck models of the created decks, deck and card ids are set. Never
     * null.
     */
    public final List<DeckModel> createdDeckModels;

    public DeckImportResult(int decksCreated, int cardsImported,
                            int duplicateCardsSkipped, List<DeckModel> createdDeckModels) {
        this.decksCreated = decksCreated;
        this.cardsImported = cardsImported;
        this.duplicateCardsSkipped = duplicateCardsSkipped;
        this.createdDeckModels = createdDeckModels == null
                ? Collections.emptyList() : createdDeckModels;
    }

    /**
     * Empty result for a no-op import.
     */
    public static DeckImportResult empty() {
        return new DeckImportResult(0, 0, 0, Collections.emptyList());
    }
}
