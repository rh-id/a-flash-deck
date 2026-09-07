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
}