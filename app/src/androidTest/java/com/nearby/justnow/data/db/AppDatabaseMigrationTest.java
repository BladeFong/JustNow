package com.nearby.justnow.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";

    @Rule
    public MigrationTestHelper helper;

    public AppDatabaseMigrationTest() {
        helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class
        );
    }

    @Test
    public void migrate8To9() throws IOException {
        // Create database with version 8 schema
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 8);

        // Insert dummy data into tasks table for testing migration data loss prevention
        ContentValues values = new ContentValues();
        values.put("content", "Test Task");
        values.put("detail", "Task details");
        values.put("quadrant", 1);
        values.put("focus_minutes", 25);
        values.put("is_archived", 0);
        values.put("created_at", System.currentTimeMillis());
        values.put("executing_start_ms", 0);
        values.put("executing_end_ms", 0);
        values.put("detail_markdown", "");
        values.put("detail_module_type", "");
        values.put("completion_mode", 0);
        values.put("quota", 1);
        values.put("icon_name", "study");
        long taskId = db.insert("tasks", SQLiteDatabase.CONFLICT_REPLACE, values);

        db.close();

        // Run MIGRATION_8_9 to upgrade to version 9
        db = helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9);

        // Verify version 8 data is intact
        Cursor cursor = db.query("SELECT * FROM tasks WHERE id = " + taskId);
        assertTrue(cursor.moveToFirst());
        assertEquals("Test Task", cursor.getString(cursor.getColumnIndexOrThrow("content")));
        assertEquals("study", cursor.getString(cursor.getColumnIndexOrThrow("icon_name")));
        cursor.close();

        // Verify new table task_photos is successfully created
        Cursor photosCursor = db.query("SELECT * FROM task_photos");
        assertNotNull(photosCursor);
        assertEquals(0, photosCursor.getCount());
        photosCursor.close();
    }
}
