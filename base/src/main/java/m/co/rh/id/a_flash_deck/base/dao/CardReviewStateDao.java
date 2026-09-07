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

import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.CardReviewState;

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

    @Query("DELETE FROM card_review_state WHERE card_id = :cardId")
    public abstract int deleteByCardId(long cardId);

    @Query("DELETE FROM card_review_state WHERE card_id IN " +
            "(SELECT id FROM card WHERE deck_id IN (:deckIds))")
    public abstract int deleteByDeckIds(List<Long> deckIds);
}
