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

package m.co.rh.id.a_flash_deck.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.Deck;

/**
 * DAO that handles deck entity
 */
@Dao
public abstract class DeckDao {

    @Transaction
    public void insertDeck(Deck deck) {
        if (deck == null) {
            return;
        }
        Date currentDate = new Date();
        if (deck.createdDateTime == null) {
            deck.createdDateTime = currentDate;
        }
        deck.updatedDateTime = deck.createdDateTime;
        deck.id = insert(deck);
    }

    @Transaction
    public void updateDeck(Deck deck) {
        if (deck == null) {
            return;
        }
        deck.updatedDateTime = new Date();
        update(deck);
    }

    @Delete
    public abstract void delete(Deck deck);

    @Query("SELECT COUNT(*) FROM deck")
    public abstract int countDeck();

    @Query("SELECT * FROM deck ORDER BY name ASC LIMIT :limit")
    public abstract List<Deck> getDeckWithLimit(int limit);

    @Query("SELECT * FROM deck WHERE name LIKE '%'||:search||'%' ORDER BY name")
    public abstract List<Deck> searchDeck(String search);

    @Query("SELECT * FROM deck WHERE id=:deckId")
    public abstract Deck getDeckById(long deckId);

    /**
     * Returns all decks whose id is in the given list (queried in batches; deduplicates and tolerates null/empty input).
     */
    public List<Deck> findDeckByIds(List<Long> deckIds) {
        return DaoBatchQueryUtil.queryInBatches(deckIds, this::getDeckByIds);
    }

    @Query("SELECT * FROM deck WHERE id IN (:deckIds)")
    abstract List<Deck> getDeckByIds(List<Long> deckIds);

    @Query("SELECT * FROM deck")
    public abstract List<Deck> getAllDecks();

    @Insert
    protected abstract long insert(Deck deck);

    @Update
    protected abstract void update(Deck deck);
}
