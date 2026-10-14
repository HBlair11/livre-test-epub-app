package com.epubreader.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        androidx.room.migration.bundle.DropAllTablesDestructiveMigrationCallback()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To16() {
        var db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO books (id, title, author, path, checksum, added_date) VALUES (1, 'Test Book', 'Test Author', 'path', 'sum', 123)")
            close()
        }

        db = helper.runMigrationsAndValidate(TEST_DB, 16, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16
        )

        val cursor = db.query("SELECT * FROM books WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("Test Book", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals("Test Author", cursor.getString(cursor.getColumnIndexOrThrow("author")))
        cursor.close()
    }
}
