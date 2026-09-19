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
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.TypeConverters;

import java.util.Date;
import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;
import m.co.rh.id.a_flash_deck.base.room.converter.Converter;

/**
 * DAO that handles card review state entity
 */
@Dao
public abstract class CardReviewStateDao {

    /**
     * Insert or update the review state of a card (upsert).
     * Rows created by grading always have dueDateTime set; a row lazily
     * created by suspending a never-studied card has dueDateTime null until
     * the first grade.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract long insertReviewState(CardReviewState cardReviewState);

    @Query("SELECT * FROM card_review_state WHERE card_id = :cardId")
    public abstract CardReviewState findByCardId(long cardId);

    /**
     * Returns the due date time of every not suspended card that has one.
     * Used by the statistics due forecast; rows with a null due_date_time
     * (new/never studied cards or rows lazily created by suspending a
     * never-studied card) have no scheduled review and are excluded.
     */
    @TypeConverters({Converter.class})
    @Query("SELECT due_date_time FROM card_review_state " +
            "WHERE suspended = 0 AND due_date_time IS NOT NULL")
    public abstract List<Date> findDueDateTimes();

    @Query("DELETE FROM card_review_state WHERE card_id = :cardId")
    public abstract int deleteByCardId(long cardId);

    /**
     * Returns the card ids among the given ones that are currently suspended
     * (queried in batches; tolerates null/empty input).
     */
    public List<Long> findSuspendedCardIdsByCardIds(List<Long> cardIds) {
        return DaoBatchQueryUtil.queryInBatches(cardIds, this::getSuspendedCardIds);
    }

    @Query("SELECT card_id FROM card_review_state WHERE card_id IN (:cardIds) AND suspended = 1")
    abstract List<Long> getSuspendedCardIds(List<Long> cardIds);

    @Query("DELETE FROM card_review_state WHERE card_id IN " +
            "(SELECT id FROM card WHERE deck_id IN (:deckIds))")
    public abstract int deleteByDeckIds(List<Long> deckIds);
}
