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

package m.co.rh.id.a_flash_deck.bot.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_flash_deck.bot.entity.CardLog;

@Dao
public abstract class CardLogDao {
    @Insert
    public abstract long insert(CardLog cardLog);

    @Delete
    public abstract void delete(CardLog cardLog);

    @Query("DELETE FROM card_log WHERE card_id = :cardId")
    public abstract void deleteCardLogsByCardId(long cardId);

    @Query("DELETE FROM card_log")
    public abstract void deleteAllCardLog();

    @Query("SELECT card_id FROM card_log WHERE created_date_time BETWEEN :createdFrom AND :createdTo")
    public abstract List<Long> findCardLogCardIdByCreatedDateFromTo(long createdFrom, long createdTo);

    @Query("SELECT COUNT(*) FROM card_log WHERE card_id = :cardId AND _action = :action AND created_date_time BETWEEN :createdFrom AND :createdTo")
    public abstract int countCardLogByCardIdAndActionAndCreatedDateFromTo(long cardId, int action, long createdFrom, long createdTo);

    @Query("SELECT card_id FROM card_log")
    public abstract List<Long> findAllCardLogCardIds();

    @Query("DELETE FROM card_log WHERE card_id IN (:cardIds)")
    public abstract void deleteCardLogsByCardIds(List<Long> cardIds);
}
