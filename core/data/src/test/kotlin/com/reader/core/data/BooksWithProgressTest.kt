package com.reader.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.reader.core.data.model.Book
import com.reader.core.database.ReaderDatabase
import com.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BooksWithProgressTest {
    private lateinit var db: ReaderDatabase
    private lateinit var repo: DefaultLibraryRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ReaderDatabase::class.java).allowMainThreadQueries().build()
        repo = DefaultLibraryRepository(db.bookDao(), db.savedWordDao())
    }
    @After fun tearDown() = db.close()

    @Test fun observeBooksWithProgress_maps_percent_and_defaults_zero() = runTest {
        val a = repo.addBook(Book(0, "A", null, null, "/a.epub", addedAt = 1L, lastOpenedAt = 10L))
        repo.addBook(Book(0, "B", null, null, "/b.epub", addedAt = 2L, lastOpenedAt = null))
        db.bookDao().upsertProgress(ReadingProgressEntity(a, null, 0.5, 3L))

        val rows = repo.observeBooksWithProgress().first()
        assertEquals(2, rows.size)
        assertEquals("A", rows[0].book.title) // most-recently-opened first
        assertEquals(0.5, rows[0].percent, 0.0001)
        assertEquals("B", rows[1].book.title)
        assertEquals(0.0, rows[1].percent, 0.0001) // no progress row → 0.0
    }
}
