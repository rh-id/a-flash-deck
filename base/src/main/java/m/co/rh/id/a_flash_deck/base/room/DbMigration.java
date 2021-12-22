package m.co.rh.id.a_flash_deck.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAllMigrations() {
        return new Migration[]{MIGRATION_1_2};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM `android_notification`");
        }
    };
}
