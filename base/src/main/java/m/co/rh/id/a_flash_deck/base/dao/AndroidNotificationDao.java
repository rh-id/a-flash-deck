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

import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;

@Dao
public abstract class AndroidNotificationDao {

    @Query("SELECT * FROM android_notification WHERE request_id = :requestId")
    public abstract AndroidNotification findByRequestId(int requestId);

    @Query("SELECT * FROM android_notification WHERE group_key=:groupKey AND ref_id=:refId")
    public abstract AndroidNotification findByGroupTagAndRefId(String groupKey, Long refId);


    @Query("SELECT COUNT(id) FROM android_notification")
    public abstract long count();

    @Query("DELETE FROM android_notification WHERE request_id = :requestId")
    public abstract void deleteByRequestId(int requestId);

    @Transaction
    public void insertNotification(AndroidNotification androidNotification) {
        long count = count();
        androidNotification.requestId = (int) (count % Integer.MAX_VALUE);
        androidNotification.id = insert(androidNotification);
    }


    @Insert
    protected abstract long insert(AndroidNotification androidNotification);

    @Delete
    public abstract void delete(AndroidNotification androidNotification);
}
