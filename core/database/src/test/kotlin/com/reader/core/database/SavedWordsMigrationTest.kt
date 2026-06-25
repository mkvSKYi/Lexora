package com.reader.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedWordsMigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-saved-test.db"

    @After fun cleanup() { ctx.deleteDatabase(dbName) }

    @Test fun migration_2_3_preserves_rows_and_defaults_learned_false() = runTest {
        ctx.deleteDatabase(dbName)
        // Build the full v2 schema (books, reading_progress, saved_words) + a saved_words row,
        // at user_version 2, so Room can validate the migrated DB against the v3 schema.
        val raw = ctx.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `books` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`author` TEXT, `coverPath` TEXT, `filePath` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_progress` (" +
                "`bookId` INTEGER NOT NULL, `locatorJson` TEXT, `percent` REAL NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`))",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_words` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL, " +
                "`translation` TEXT NOT NULL, `contextSentence` TEXT, `bookId` INTEGER NOT NULL, " +
                "`bookTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_words_term_bookId` " +
                "ON `saved_words` (`term`, `bookId`)",
        )
        raw.execSQL(
            "INSERT INTO saved_words (term, translation, contextSentence, bookId, bookTitle, createdAt) " +
                "VALUES ('dog','собака',NULL,1,'Book',5)",
        )
        raw.version = 2
        raw.close()

        val db = Room.databaseBuilder(ctx, ReaderDatabase::class.java, dbName)
            .addMigrations(
                ReaderDatabase.MIGRATION_1_2,
                ReaderDatabase.MIGRATION_2_3,
                ReaderDatabase.MIGRATION_3_4,
                ReaderDatabase.MIGRATION_4_5,
                ReaderDatabase.MIGRATION_5_6,
            )
            .build()
        val rows = db.savedWordDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals("dog", rows[0].term)
        assertFalse(rows[0].learned)
        db.close()
    }

    @Test fun migrate3To4_preservesRow_andDefaultsSchedulingColumns() = runTest {
        ctx.deleteDatabase(dbName)
        // Build the full v3 schema (books, reading_progress, saved_words with learned) + a row,
        // at user_version 3, so Room can validate the migrated DB against the v4 schema.
        val raw = ctx.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `books` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`author` TEXT, `coverPath` TEXT, `filePath` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_progress` (" +
                "`bookId` INTEGER NOT NULL, `locatorJson` TEXT, `percent` REAL NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`))",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_words` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL, " +
                "`translation` TEXT NOT NULL, `contextSentence` TEXT, `bookId` INTEGER NOT NULL, " +
                "`bookTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`learned` INTEGER NOT NULL DEFAULT 0)",
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_words_term_bookId` " +
                "ON `saved_words` (`term`, `bookId`)",
        )
        raw.execSQL(
            "INSERT INTO saved_words (term, translation, contextSentence, bookId, bookTitle, createdAt, learned) " +
                "VALUES ('room', 'кімната', 'a quiet room', 7, 'Atomic Habits', 100, 0)",
        )
        raw.version = 3
        raw.close()

        val db = Room.databaseBuilder(ctx, ReaderDatabase::class.java, dbName)
            .addMigrations(
                ReaderDatabase.MIGRATION_1_2,
                ReaderDatabase.MIGRATION_2_3,
                ReaderDatabase.MIGRATION_3_4,
                ReaderDatabase.MIGRATION_4_5,
                ReaderDatabase.MIGRATION_5_6,
            )
            .build()
        val rows = db.savedWordDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals("room", rows[0].term)
        assertEquals(2.5, rows[0].easeFactor, 1e-9)
        assertEquals(0, rows[0].intervalDays)
        assertEquals(0, rows[0].repetitions)
        assertEquals(0L, rows[0].dueAt)
        assertTrue(rows[0].lastReviewedAt == null)
        db.close()
    }

    @Test fun migrate4To5_addsBookmarks_andPreservesData() = runTest {
        ctx.deleteDatabase(dbName)
        // Build the full v4 schema (books, reading_progress, saved_words with scheduling cols) + a row,
        // at user_version 4, so Room can validate the migrated DB against the v5 schema.
        val raw = ctx.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `books` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`author` TEXT, `coverPath` TEXT, `filePath` TEXT NOT NULL, " +
                "`addedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_progress` (" +
                "`bookId` INTEGER NOT NULL, `locatorJson` TEXT, `percent` REAL NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`))",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_words` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL, " +
                "`translation` TEXT NOT NULL, `contextSentence` TEXT, `bookId` INTEGER NOT NULL, " +
                "`bookTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`learned` INTEGER NOT NULL, `easeFactor` REAL NOT NULL, " +
                "`intervalDays` INTEGER NOT NULL, `repetitions` INTEGER NOT NULL, " +
                "`dueAt` INTEGER NOT NULL, `lastReviewedAt` INTEGER)",
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_words_term_bookId` " +
                "ON `saved_words` (`term`, `bookId`)",
        )
        raw.execSQL(
            "INSERT INTO saved_words " +
                "(term, translation, contextSentence, bookId, bookTitle, createdAt, " +
                "learned, easeFactor, intervalDays, repetitions, dueAt, lastReviewedAt) " +
                "VALUES ('mark', 'позначка', 'a clear mark', 7, 'Bookmarks', 100, 0, 2.5, 0, 0, 0, NULL)",
        )
        raw.version = 4
        raw.close()

        val db = Room.databaseBuilder(ctx, ReaderDatabase::class.java, dbName)
            .addMigrations(
                ReaderDatabase.MIGRATION_1_2,
                ReaderDatabase.MIGRATION_2_3,
                ReaderDatabase.MIGRATION_3_4,
                ReaderDatabase.MIGRATION_4_5,
                ReaderDatabase.MIGRATION_5_6,
            )
            .build()

        // (a) pre-existing saved_words sentinel survived the migration.
        val rows = db.savedWordDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals("mark", rows[0].term)

        // (b) the bookmarks table exists with the right columns and round-trips an insert+read.
        val bookmarkId = db.bookmarkDao().insert(
            com.reader.core.database.entity.BookmarkEntity(
                bookId = 7,
                locatorJson = "{}",
                href = "ch1.html",
                progression = 0.5,
                totalProgression = 0.25,
                chapterTitle = "Chapter 1",
                createdAt = 100,
            ),
        )
        assertTrue(bookmarkId > 0)
        val bookmarks = db.bookmarkDao().observeForBook(7).first()
        assertEquals(1, bookmarks.size)
        assertEquals("ch1.html", bookmarks[0].href)
        assertEquals(0.5, bookmarks[0].progression, 1e-9)
        assertEquals("Chapter 1", bookmarks[0].chapterTitle)
        db.close()
    }
}
