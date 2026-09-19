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

package m.co.rh.id.a_flash_deck.base;

import android.database.Cursor;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import m.co.rh.id.a_flash_deck.base.room.AppDatabase;
import m.co.rh.id.a_flash_deck.base.room.DbMigration;

@RunWith(AndroidJUnit4.class)
public class DbMigrationTest {
    private static final String TEST_DB = DbMigrationTest.class.getName()
            + "-migration-test";
    private static final String TEST_DB_14_15 = DbMigrationTest.class.getName()
            + "-migration-test-14-15";
    private static final String TEST_DB_15_16 = DbMigrationTest.class.getName()
            + "-migration-test-15-16";

    @Rule
    public MigrationTestHelper helper;

    public DbMigrationTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Test
    public void migrateAll() throws IOException {
        // Create earliest version of the database.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        db.close();

        // Open latest version of the database. Room will validate the schema
        // once all migrations execute.
        AppDatabase appDb = Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class,
                TEST_DB)
                .addMigrations(DbMigration.getAllMigrations()).build();
        appDb.getOpenHelper().getWritableDatabase();
        appDb.close();
    }

    @Test
    public void migrate14To15() throws IOException {
        // Create the v14 database (card_review_state does not exist yet,
        // so there is nothing to insert pre-migration).
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB_14_15, 14);
        db.close();

        // Run MIGRATION_14_15 and validate the resulting schema against v15.
        SupportSQLiteDatabase migratedDb = helper.runMigrationsAndValidate(
                TEST_DB_14_15, 15, true, DbMigration.MIGRATION_14_15);

        // Insert a row omitting the suspended column; it must default
        // to not suspended.
        migratedDb.execSQL("INSERT INTO card_review_state " +
                "(card_id, due_date_time, interval_days, ease_factor, " +
                "repetitions, lapses, last_review_date_time) " +
                "VALUES (1, 100, 1.0, 2.5, 1, 0, 100)");
        Cursor cursor = migratedDb.query(
                "SELECT suspended FROM card_review_state WHERE card_id = 1");
        Assert.assertTrue(cursor.moveToFirst());
        Assert.assertEquals(0, cursor.getInt(
                cursor.getColumnIndexOrThrow("suspended")));
        cursor.close();
        migratedDb.close();
    }

    @Test
    public void migrate15To16() throws IOException {
        // Create the v15 database with an existing card and its review state
        // (the review_log table does not exist yet).
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB_15_16, 15);
        db.execSQL("INSERT INTO deck (name, created_date_time, updated_date_time) " +
                "VALUES ('deck1', 100, 100)");
        db.execSQL("INSERT INTO card (deck_id, ordinal, question, answer, " +
                "is_reversible_qa) VALUES (1, 0, 'q', 'a', 0)");
        db.execSQL("INSERT INTO card_review_state (card_id, due_date_time, " +
                "interval_days, ease_factor, repetitions, lapses, " +
                "last_review_date_time, suspended) " +
                "VALUES (1, 100, 1.0, 2.5, 1, 0, 100, 0)");
        db.close();

        // Run MIGRATION_15_16 and validate the resulting schema against v16.
        SupportSQLiteDatabase migratedDb = helper.runMigrationsAndValidate(
                TEST_DB_15_16, 16, true, DbMigration.MIGRATION_15_16);

        // The review log starts empty (honest empty start, no backfill) and
        // the pre-existing rows are untouched.
        Cursor cursor = migratedDb.query("SELECT COUNT(*) FROM review_log");
        Assert.assertTrue(cursor.moveToFirst());
        Assert.assertEquals(0, cursor.getInt(0));
        cursor.close();
        cursor = migratedDb.query(
                "SELECT due_date_time FROM card_review_state WHERE card_id = 1");
        Assert.assertTrue(cursor.moveToFirst());
        Assert.assertEquals(100, cursor.getLong(
                cursor.getColumnIndexOrThrow("due_date_time")));
        cursor.close();
        migratedDb.close();
    }
}