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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;
import m.co.rh.id.a_flash_deck.base.model.DeckModel;

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

    @Query("SELECT * FROM deck ORDER BY name ASC LIMIT :limit")
    public abstract List<Deck> getDeckWithLimit(int limit);

    @Query("SELECT * FROM deck WHERE name LIKE '%'||:search||'%' ORDER BY name")
    public abstract List<Deck> searchDeck(String search);

    @Query("SELECT * FROM card ORDER BY ordinal ASC LIMIT :limit")
    public abstract List<Card> getCardWithLimit(int limit);

    @Query("SELECT * FROM card WHERE deck_id=:deckId ORDER BY ordinal ASC LIMIT :limit")
    public abstract List<Card> getCardByDeckIdWithLimit(long deckId, int limit);

    @Query("SELECT * FROM card WHERE deck_id=:deckId ORDER BY ordinal")
    public abstract List<Card> getCardByDeckId(long deckId);

    @Query("SELECT * FROM card WHERE question LIKE '%'||:search||'%' " +
            "OR answer LIKE '%'||:search||'%' ORDER BY ordinal")
    public abstract List<Card> searchCard(String search);

    @Query("SELECT * FROM card WHERE deck_id=:deckId AND (question LIKE '%'||:search||'%' " +
            "OR answer LIKE '%'||:search||'%') ORDER BY ordinal")
    public abstract List<Card> searchCardByDeckId(long deckId, String search);

    @Query("SELECT * FROM deck WHERE id=:deckId")
    public abstract Deck getDeckById(long deckId);

    @Query("SELECT * FROM card WHERE deck_id IN (:deckIds)")
    public abstract List<Card> getCardByDeckIds(List<Long> deckIds);

    @Query("SELECT * FROM card WHERE id =:cardId")
    public abstract Card getCardByCardId(long cardId);

    @Query("SELECT * FROM deck WHERE id IN (:deckIds)")
    public abstract List<Deck> getDeckByIds(List<Long> deckIds);

    @Query("SELECT * FROM deck")
    public abstract List<Deck> getAllDecks();

    @Transaction
    public void importDecks(List<DeckModel> deckModels) {
        if (deckModels == null || deckModels.isEmpty()) return;
        for (DeckModel deckModel : deckModels) {
            Deck deck = deckModel.getDeck();
            // imported deck id and our deck id must not same
            deck.id = null;
            deck.id = insert(deck);
            List<Card> cardList = deckModel.getCardList();
            if (cardList != null && !cardList.isEmpty()) {
                for (Card card : cardList) {
                    // replace imported deck id with our deck id
                    card.deckId = deck.id;
                    card.id = null;
                    card.id = insert(card);
                }
            }
        }
    }

    public List<Card> getCardsByDecks(List<Deck> decks) {
        if (decks == null || decks.isEmpty()) return new ArrayList<>();
        List<Long> deckIds = new ArrayList<>();
        for (Deck deck : decks) {
            deckIds.add(deck.id);
        }
        return getCardByDeckIds(deckIds);
    }

    @Transaction
    public void moveCardToDeck(Card card, Deck deck) {
        if (card == null || deck == null || card.id == null || deck.id == null) {
            return;
        }
        card.deckId = deck.id;
        update(card);
    }

    @Transaction
    public void copyCardToDeck(Card card, Deck deck) {
        if (card == null || deck == null || card.id == null || deck.id == null) {
            return;
        }
        card.deckId = deck.id;
        card.id = null;
        card.id = insert(card);
    }

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
