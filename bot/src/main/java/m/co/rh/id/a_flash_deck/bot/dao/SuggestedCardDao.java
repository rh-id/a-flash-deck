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
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import m.co.rh.id.a_flash_deck.bot.entity.SuggestedCard;

@Dao
public abstract class SuggestedCardDao {
    @Insert
    public abstract long insert(SuggestedCard suggestedCard);

    @Query("DELETE FROM suggested_card")
    public abstract void deleteAllSuggestedCard();

    @Query("SELECT COUNT(*) FROM suggested_card")
    public abstract int countSuggestedCard();

    @Query("DELETE FROM suggested_card WHERE card_id = :cardId")
    public abstract void deleteSuggestedCardByCardId(long cardId);

    @Query("SELECT card_id FROM suggested_card")
    public abstract List<Long> findAllSuggestedCardCardIds();

    @Query("DELETE FROM suggested_card WHERE card_id IN (:cardIds)")
    public abstract void deleteSuggestedCardByCardIds(List<Long> cardIds);

    @Query("SELECT * FROM suggested_card")
    public abstract List<SuggestedCard> findAllSuggestedCards();
}
