package com.reader.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.reader.core.data.model.Book
import com.reader.core.database.ReaderDatabase
import com.reader.core.database.entity.BookmarkEntity
import com.reader.core.database.entity.ReadingProgressEntity
import com.reader.core.database.entity.SavedWordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DefaultLibraryRepositoryDeleteTest {
    private lateinit var db: ReaderDatabase
    private lateinit var repo: DefaultLibraryRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ReaderDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DefaultLibraryRepository(db.bookDao(), db.savedWordDao(), db.bookmarkDao())
    }
    @After fun tearDown() = db.close()

    private fun tempFile(name: String): File =
        File.createTempFile(name, ".tmp").apply { writeText("x") }

    @Test fun deleteBookCompletely_removes_files_and_all_rows() = runTest {
        val epub = tempFile("book"); val cover = tempFile("cover")
        val id = repo.addBook(
            Book(0, "T", "A", cover.absolutePath, epub.absolutePath, addedAt = 1L, lastOpenedAt = null),
        )
        db.bookDao().upsertProgress(ReadingProgressEntity(id, "{}", 0.5, 2L))
        db.savedWordDao().upsert(SavedWordEntity(0, "w", "в", null, id, "T", 3L))
        db.bookmarkDao().insert(BookmarkEntity(0, id, "{}", "ch1.html", 0.5, 0.25, "C1", 4L))
        // A second book must remain untouched.
        val otherEpub = tempFile("book2")
        val otherId = repo.addBook(Book(0, "T2", null, null, otherEpub.absolutePath, 1L, null))
        db.savedWordDao().upsert(SavedWordEntity(0, "w2", "в2", null, otherId, "T2", 3L))
        db.bookmarkDao().insert(BookmarkEntity(0, otherId, "{}", "ch2.html", 0.1, 0.1, "C2", 4L))

        repo.deleteBookCompletely(repo.getBook(id)!!)

        assertFalse(epub.exists()); assertFalse(cover.exists())
        assertNull(repo.getBook(id))
        assertNull(db.bookDao().getProgress(id))
        assertEquals(0, db.savedWordDao().observeAll().first().count { it.bookId == id })
        assertEquals(0, db.bookmarkDao().observeForBook(id).first().size)
        // other book intact
        assertTrue(otherEpub.exists())
        assertEquals("T2", repo.getBook(otherId)?.title)
        assertEquals(1, db.savedWordDao().observeAll().first().count { it.bookId == otherId })
        assertEquals(1, db.bookmarkDao().observeForBook(otherId).first().size)
    }

    @Test fun progressPercent_returns_value_or_zero() = runTest {
        val id = repo.addBook(Book(0, "T", null, null, "/x.epub", 1L, null))
        assertEquals(0.0, repo.progressPercent(id), 0.0)        // no row yet
        db.bookDao().upsertProgress(ReadingProgressEntity(id, null, 0.42, 2L))
        assertEquals(0.42, repo.progressPercent(id), 0.0001)
    }
}
