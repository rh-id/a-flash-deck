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

import androidx.room.ColumnInfo;
import androidx.room.Ignore;

import java.io.Serializable;

/**
 * Per-deck row of the statistics page: due/new card count of a deck,
 * enriched with the deck name and total card count by StatsCmd
 */
public class DeckDueCount implements Serializable {

    /**
     * Deck ID that refers to Deck.id
     */
    @ColumnInfo(name = "deckId")
    public long deckId;

    /**
     * Count of due or new cards in the deck
     */
    @ColumnInfo(name = "count")
    public int count;

    /**
     * Deck name, filled after the query (not mapped by Room)
     */
    @Ignore
    public String deckName;

    /**
     * Total card count of the deck, filled after the query (not mapped by Room)
     */
    @Ignore
    public int totalCards;
}
