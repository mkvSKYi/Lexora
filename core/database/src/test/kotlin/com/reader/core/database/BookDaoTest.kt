package com.reader.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.reader.core.database.dao.BookDao
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookDaoTest {
    private lateinit var db: ReaderDatabase
    private lateinit var dao: BookDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bookDao()
    }

    @After fun teardown() = db.close()

    @Test fun upsert_then_observe_returns_book() = runTest {
        val id = dao.upsertBook(
            BookEntity(0, "Dune", "Herbert", null, "/books/dune.epub", 100L, null),
        )
        dao.observeBooks().test {
            val books = awaitItem()
            assertEquals(1, books.size)
            assertEquals("Dune", books.first().title)
            assertEquals(id, books.first().id)
        }
    }

    @Test fun progress_roundtrips() = runTest {
        val id = dao.upsertBook(BookEntity(0, "Dune", null, null, "/d.epub", 1L, null))
        dao.upsertProgress(ReadingProgressEntity(id, "{\"href\":\"ch1\"}", 0.42, 5L))
        val p = dao.getProgress(id)
        assertEquals(0.42, p!!.percent, 0.0001)
        assertEquals("{\"href\":\"ch1\"}", p.locatorJson)
    }
}
