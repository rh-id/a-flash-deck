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
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.model.DeckDueCount;

/**
 * DAO that handles study selection queries
 */
@Dao
public abstract class StudyDao {

    /**
     * A card is selectable when it has no review-state row (new/never studied)
     * or when its row is not suspended and it is due.
     * The suspended and due checks MUST stay INSIDE the due branch: for a
     * missing row (LEFT JOIN) every card_review_state column is NULL and
     * both "NULL = 0" and "NULL <= :now" evaluate to NULL (not true), which
     * would drop never-studied cards. due_date_time is also NULL for a row
     * lazily created by suspending a never-studied card, so the explicit
     * IS NULL check keeps such cards selectable again after unsuspend.
     */
    @Query("SELECT card.* FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE (card_review_state.card_id IS NULL " +
            "OR (card_review_state.suspended = 0 " +
            "AND (card_review_state.due_date_time IS NULL OR card_review_state.due_date_time <= :now))) " +
            "ORDER BY card.ordinal ASC, card.id ASC")
    public abstract List<Card> findDueAndNewCards(long now);

    @Query("SELECT COUNT(*) FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE (card_review_state.card_id IS NULL " +
            "OR (card_review_state.suspended = 0 " +
            "AND (card_review_state.due_date_time IS NULL OR card_review_state.due_date_time <= :now)))")
    public abstract int countDueAndNewCards(long now);

    /**
     * A card is new when it has no review-state row (never studied), so the
     * count is simply the number of cards missing a review-state row.
     */
    @Query("SELECT COUNT(*) FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE card_review_state.card_id IS NULL")
    public abstract int countNewCards();

    /**
     * Per-deck count of due or new cards. Same due/null handling as
     * {@link #countDueAndNewCards(long)}: the suspended and due checks MUST
     * stay INSIDE the due branch, because for a missing row (LEFT JOIN) every
     * card_review_state column is NULL and both "NULL = 0" and "NULL <= :now"
     * evaluate to NULL (not true), which would drop never-studied cards.
     * due_date_time is also NULL for a row lazily created by suspending a
     * never-studied card, so the explicit IS NULL check keeps such cards
     * counted after unsuspend.
     */
    @Query("SELECT card.deck_id AS deckId, COUNT(*) AS count " +
            "FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE (card_review_state.card_id IS NULL " +
            "OR (card_review_state.suspended = 0 " +
            "AND (card_review_state.due_date_time IS NULL OR card_review_state.due_date_time <= :now))) " +
            "GROUP BY card.deck_id")
    public abstract List<DeckDueCount> countDueAndNewCardsByDeckId(long now);

    /**
     * Count of due or new cards of a single deck. Same due/null handling as
     * {@link #countDueAndNewCards(long)}: the suspended and due checks MUST
     * stay INSIDE the due branch, because for a missing row (LEFT JOIN) every
     * card_review_state column is NULL and both "NULL = 0" and "NULL <= :now"
     * evaluate to NULL (not true), which would drop never-studied cards.
     * due_date_time is also NULL for a row lazily created by suspending a
     * never-studied card, so the explicit IS NULL check keeps such cards
     * counted after unsuspend.
     */
    @Query("SELECT COUNT(*) FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE card.deck_id = :deckId " +
            "AND (card_review_state.card_id IS NULL " +
            "OR (card_review_state.suspended = 0 " +
            "AND (card_review_state.due_date_time IS NULL OR card_review_state.due_date_time <= :now)))")
    public abstract int countDueAndNewCardsBySingleDeckId(long deckId, long now);

    /**
     * Cards in the given decks that are due or new. Same due/null handling as
     * {@link #countDueAndNewCards(long)}: the suspended and due checks MUST
     * stay INSIDE the due branch, because for a missing row (LEFT JOIN) every
     * card_review_state column is NULL and both "NULL = 0" and "NULL <= :now"
     * evaluate to NULL (not true), which would drop never-studied cards.
     * due_date_time is also NULL for a row lazily created by suspending a
     * never-studied card, so the explicit IS NULL check keeps such cards
     * selectable again after unsuspend.
     * Batching mirrors CardDao.findCardByDeckIds; ordering is not needed (caller shuffles).
     */
    public List<Card> findDueAndNewCardsByDeckIds(List<Long> deckIds, long now) {
        return DaoBatchQueryUtil.queryInBatches(deckIds, deckIdsBatch ->
                getDueAndNewCardsByDeckIds(deckIdsBatch, now));
    }

    @Query("SELECT card.* FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE card.deck_id IN (:deckIds) " +
            "AND (card_review_state.card_id IS NULL " +
            "OR (card_review_state.suspended = 0 " +
            "AND (card_review_state.due_date_time IS NULL OR card_review_state.due_date_time <= :now)))")
    abstract List<Card> getDueAndNewCardsByDeckIds(List<Long> deckIds, long now);

    /**
     * Cards in the given decks that are not suspended. A missing review-state row
     * (LEFT JOIN NULL) means a new/never-studied card, which is not suspended.
     * Batching mirrors CardDao.findCardByDeckIds; ordering is not needed (caller shuffles).
     */
    public List<Card> findNonSuspendedCardsByDeckIds(List<Long> deckIds) {
        return DaoBatchQueryUtil.queryInBatches(deckIds, this::getNonSuspendedCardsByDeckIds);
    }

    @Query("SELECT card.* FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE card.deck_id IN (:deckIds) " +
            "AND (card_review_state.card_id IS NULL OR card_review_state.suspended = 0)")
    abstract List<Card> getNonSuspendedCardsByDeckIds(List<Long> deckIds);

    /**
     * Same as findNonSuspendedCardsByDeckIds but filtered by card ids (bot suggestion flow).
     * A missing review-state row (LEFT JOIN NULL) means a new/never-studied card,
     * which is not suspended.
     */
    public List<Card> findNonSuspendedCardsByCardIds(List<Long> cardIds) {
        return DaoBatchQueryUtil.queryInBatches(cardIds, this::getNonSuspendedCardsByCardIds);
    }

    @Query("SELECT card.* FROM card LEFT JOIN card_review_state ON card.id = card_review_state.card_id " +
            "WHERE card.id IN (:cardIds) " +
            "AND (card_review_state.card_id IS NULL OR card_review_state.suspended = 0)")
    abstract List<Card> getNonSuspendedCardsByCardIds(List<Long> cardIds);
}
