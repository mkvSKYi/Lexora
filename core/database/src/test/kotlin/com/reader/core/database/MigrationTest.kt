package com.reader.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReaderDatabase::class.java,
    )

    @Test fun migrate_1_to_2_preserves_books_and_adds_saved_words() {
        val dbName = "migration-test"
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO books (id,title,author,coverPath,filePath,addedAt,lastOpenedAt) " +
                    "VALUES (1,'Dune',null,null,'/d.epub',1,null)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, ReaderDatabase.MIGRATION_1_2)
        val booksCursor = db.query("SELECT title FROM books WHERE id = 1")
        assertTrue(booksCursor.moveToFirst())
        assertEquals("Dune", booksCursor.getString(0))
        booksCursor.close()
        val savedCursor = db.query("SELECT COUNT(*) FROM saved_words")
        assertTrue(savedCursor.moveToFirst())
        assertEquals(0, savedCursor.getInt(0))
        savedCursor.close()
        db.close()
    }

    @Test fun migrate_5_to_6_adds_empty_activity_days_table() {
        val dbName = "migration-5-6"
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO books (id,title,author,coverPath,filePath,addedAt,lastOpenedAt) " +
                    "VALUES (1,'Dune',null,null,'/d.epub',1,null)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 6, true, ReaderDatabase.MIGRATION_5_6)
        // The book survives and the new activity_days table exists and is empty.
        val books = db.query("SELECT title FROM books WHERE id = 1")
        assertTrue(books.moveToFirst())
        assertEquals("Dune", books.getString(0))
        books.close()
        val activity = db.query("SELECT COUNT(*) FROM activity_days")
        assertTrue(activity.moveToFirst())
        assertEquals(0, activity.getInt(0))
        activity.close()
        db.close()
    }
}
