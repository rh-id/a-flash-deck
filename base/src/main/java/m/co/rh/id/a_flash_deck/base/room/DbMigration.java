package m.co.rh.id.a_flash_deck.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAllMigrations() {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3,
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM `android_notification`");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM `android_notification`");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM android_notification");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM android_notification");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM android_notification");
        }
    };
}
