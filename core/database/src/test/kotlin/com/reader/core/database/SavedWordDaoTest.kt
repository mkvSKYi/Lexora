package com.reader.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.reader.core.database.dao.SavedWordDao
import com.reader.core.database.entity.SavedWordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedWordDaoTest {
    private lateinit var db: ReaderDatabase
    private lateinit var dao: SavedWordDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.savedWordDao()
    }

    @After fun teardown() = db.close()

    @Test fun upsert_then_observe_newest_first() = runTest {
        dao.upsert(SavedWordEntity(term = "dog", translation = "собака", contextSentence = "A dog ran.", bookId = 1, bookTitle = "B", createdAt = 100))
        dao.upsert(SavedWordEntity(term = "cat", translation = "кіт", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 200))
        dao.observeAll().test {
            val list = awaitItem()
            assertEquals(listOf("cat", "dog"), list.map { it.term })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun unique_term_book_upsert_replaces() = runTest {
        dao.upsert(SavedWordEntity(term = "dog", translation = "собака", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 100))
        dao.upsert(SavedWordEntity(term = "dog", translation = "пес", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 300))
        dao.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("пес", list.first().translation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun exists_and_delete() = runTest {
        dao.upsert(SavedWordEntity(id = 0, term = "dog", translation = "собака", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 100))
        assertTrue(dao.existsByTermAndBook("dog", 1))
        dao.observeAll().test {
            val saved = awaitItem().first()
            dao.deleteById(saved.id)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(dao.existsByTermAndBook("dog", 1))
    }
}
