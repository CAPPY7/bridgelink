package app.bridgelink.android;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Opening history must keep the rows. There is no wipe-on-upgrade path. */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {
    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            HistoryDatabase.class);

    @Test
    public void versionOneKeepsTheRow() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase("history.db", 1);
        db.execSQL("INSERT INTO lines (line) VALUES ('kept')");
        db.close();
        db = helper.runMigrationsAndValidate("history.db", 1, true);
        try (Cursor cursor = db.query("SELECT line FROM lines")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("kept", cursor.getString(0));
        }
        db.close();
    }

    @Test
    public void versionTwoKeepsTheOldRow() throws Exception {
        SupportSQLiteDatabase db = helper.createDatabase("history-two.db", 1);
        db.execSQL("INSERT INTO lines (line) VALUES ('kept')");
        db.close();
        db = helper.runMigrationsAndValidate("history-two.db", 2, true, HistoryDatabase.MIGRATION_1_2);
        try (Cursor cursor = db.query("SELECT line, kind, direction, status FROM lines")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("kept", cursor.getString(0));
            assertEquals("text", cursor.getString(1));
            assertEquals("in", cursor.getString(2));
            assertEquals("done", cursor.getString(3));
        }
        db.close();
    }
}
