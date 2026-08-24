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
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;

@Dao
public abstract class NotificationTimerDao {

    @Query("SELECT * FROM notification_timer WHERE id=:id")
    public abstract NotificationTimer findById(long id);

    @Query("SELECT * FROM notification_timer ORDER BY name ASC LIMIT :limit")
    public abstract List<NotificationTimer> getAllWithLimit(int limit);

    @Query("SELECT * FROM notification_timer WHERE name LIKE '%'||:search||'%' ORDER BY name ASC")
    public abstract List<NotificationTimer> search(String search);

    @Transaction
    public void insertNotificationTimer(NotificationTimer notificationTimer) {
        if (notificationTimer == null) {
            return;
        }
        notificationTimer.id = insert(notificationTimer);
    }

    @Insert
    protected abstract long insert(NotificationTimer notificationTimer);

    @Delete
    public abstract void delete(NotificationTimer notificationTimer);

    @Update
    public abstract void update(NotificationTimer notificationTimer);
}
