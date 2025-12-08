package m.co.rh.id.a_flash_deck.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAllMigrations() {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3,
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11};
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

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM android_notification");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // remove unused current_state field
            database.execSQL("CREATE TABLE IF NOT EXISTS " +
                    "`test_new` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`state_file_location` TEXT, " +
                    "`created_date_time` INTEGER)");
            database.execSQL("INSERT INTO test_new SELECT id,state_file_location," +
                    "created_date_time FROM test");
            database.execSQL("DROP TABLE test");
            database.execSQL("ALTER TABLE test_new RENAME TO test");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // remove unused current_state field
            database.execSQL("ALTER TABLE card ADD COLUMN `question_image` TEXT");
            database.execSQL("ALTER TABLE card ADD COLUMN `answer_image` TEXT");
            database.execSQL("DELETE FROM test");
        }
    };

    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE card ADD COLUMN `question_voice` TEXT");
        }
    };

    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE card ADD COLUMN `answer_voice` TEXT");
            database.execSQL("ALTER TABLE card ADD COLUMN `is_reversible_qa` INTEGER NOT NULL DEFAULT 0");
        }
    };
}
