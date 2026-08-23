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
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;

/**
 * DAO that handles card entity
 */
@Dao
public abstract class CardDao {

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

    @Query("SELECT * FROM card WHERE id =:cardId")
    public abstract Card getCardByCardId(long cardId);

    /**
     * Returns all cards whose deck id is in the given list (queried in batches; deduplicates and tolerates null/empty input).
     */
    public List<Card> findCardByDeckIds(List<Long> deckIds) {
        return DaoBatchQueryUtil.queryInBatches(deckIds, this::getCardByDeckIds);
    }

    @Query("SELECT * FROM card WHERE deck_id IN (:deckIds)")
    abstract List<Card> getCardByDeckIds(List<Long> deckIds);

    @Query("SELECT * FROM card WHERE question_image=:questionImage")
    public abstract Card findCardByQuestionImage(String questionImage);

    @Query("SELECT * FROM card WHERE answer_image=:answerImage")
    public abstract Card findCardByAnswerImage(String answerImage);

    @Query("SELECT * FROM card WHERE question_voice=:questionVoice")
    public abstract Card findCardByQuestionVoice(String questionVoice);

    @Query("SELECT * FROM card WHERE answer_voice=:answerVoice")
    public abstract Card findCardByAnswerVoice(String answerVoice);

    /**
     * Returns all card ids whose id is in the given list (queried in batches; deduplicates and tolerates null/empty input).
     */
    public List<Long> findCardIdsByCardIds(List<Long> cardIds) {
        return DaoBatchQueryUtil.queryInBatches(cardIds, this::getCardIdsByCardIds);
    }

    @Query("SELECT id FROM card WHERE id IN (:cardIds)")
    abstract List<Long> getCardIdsByCardIds(List<Long> cardIds);

    @Query("SELECT * FROM card WHERE id IN (:cardIds)")
    abstract List<Card> getCardsByCardIds(List<Long> cardIds);

    /**
     * Returns all cards whose id is in the given list (queried in batches; deduplicates and tolerates null/empty input).
     */
    public List<Card> findCardsByCardIds(List<Long> cardIds) {
        return DaoBatchQueryUtil.queryInBatches(cardIds, this::getCardsByCardIds);
    }

    @Query("SELECT COUNT(*) FROM card WHERE deck_id = :deckId")
    public abstract int countCardByDeckId(long deckId);

    @Query("DELETE FROM card WHERE deck_id = :deckId")
    public abstract void deleteCardsByDeckId(long deckId);

    public List<Card> getCardsByDecks(List<Deck> decks) {
        if (decks == null || decks.isEmpty()) return new ArrayList<>();
        List<Long> deckIds = new ArrayList<>();
        for (Deck deck : decks) {
            deckIds.add(deck.id);
        }
        return findCardByDeckIds(deckIds);
    }

    @Query("SELECT IFNULL(MAX(ordinal), -1) + 1 FROM card WHERE deck_id = :deckId")
    public abstract int nextOrdinal(long deckId);

    @Transaction
    public void insertCard(Card card) {
        if (card == null) {
            return;
        }
        card.ordinal = nextOrdinal(card.deckId);
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

    @Insert
    protected abstract long insert(Card card);

    @Update
    protected abstract void update(Card card);

    @Delete
    protected abstract void delete(Card card);
}
