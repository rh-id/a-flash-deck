package m.co.rh.id.a_flash_deck.base;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
}