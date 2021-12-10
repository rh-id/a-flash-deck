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
