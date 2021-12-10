package m.co.rh.id.a_flash_deck.base.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import m.co.rh.id.a_flash_deck.base.entity.AndroidNotification;

@Dao
public abstract class AndroidNotificationDao {

    @Query("SELECT * FROM android_notification WHERE request_id = :requestId")
    public abstract AndroidNotification findByRequestId(int requestId);

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
}
