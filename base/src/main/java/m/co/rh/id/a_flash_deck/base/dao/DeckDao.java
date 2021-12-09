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

package m.co.rh.id.a_flash_deck.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Date;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;

/**
 * DAO that handles deck and cards
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

    @Transaction
    public void deleteDeck(Deck deck) {
        if (deck == null) {
            return;
        }
        delete(deck);
        deleteCardsByDeckId(deck.id);
    }

    @Query("DELETE FROM card WHERE deck_id = :deckId")
    public abstract void deleteCardsByDeckId(long deckId);

    @Query("SELECT COUNT(*) FROM card WHERE deck_id = :deckId")
    public abstract int countCardByDeckId(long deckId);

    @Query("SELECT COUNT(*) FROM deck")
    public abstract int countDeck();

    @Transaction
    public void insertCard(Card card) {
        if (card == null) {
            return;
        }
        card.ordinal = countCardByDeckId(card.deckId);
        card.id = insert(card);
    }

    @Transaction
    public void updateCard(Card card) {
        if (card == null) {
            return;
        }
        update(card);
    }

    @Transaction
    public void deleteCard(Card card) {
        if (card == null) {
            return;
        }
        delete(card);
    }

    @Insert
    protected abstract long insert(Deck deck);

    @Update
    protected abstract void update(Deck deck);

    @Delete
    protected abstract void delete(Deck deck);

    @Insert
    protected abstract long insert(Card card);

    @Update
    protected abstract void update(Card card);

    @Delete
    protected abstract void delete(Card card);
}
