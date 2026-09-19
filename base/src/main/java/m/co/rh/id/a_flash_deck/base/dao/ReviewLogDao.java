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
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.ReviewLog;

/**
 * DAO that handles review log entity
 */
@Dao
public abstract class ReviewLogDao {

    @Insert
    public abstract long insertReviewLog(ReviewLog reviewLog);

    @Query("DELETE FROM review_log WHERE card_id = :cardId")
    public abstract int deleteByCardId(long cardId);

    @Query("DELETE FROM review_log WHERE card_id IN " +
            "(SELECT id FROM card WHERE deck_id IN (:deckIds))")
    public abstract int deleteByDeckIds(List<Long> deckIds);

    @Query("SELECT * FROM review_log WHERE created_date_time >= :since")
    public abstract List<ReviewLog> findReviewLogsSince(long since);

    @Query("SELECT COUNT(*) FROM review_log")
    public abstract int countAll();
}
