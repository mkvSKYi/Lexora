package com.reader.core.data

import app.cash.turbine.test
import com.reader.core.data.model.Book
import com.reader.core.database.dao.BookDao
import com.reader.core.database.dao.BookmarkDao
import com.reader.core.database.dao.SavedWordDao
import com.reader.core.database.entity.BookEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRepositoryTest {
    private val dao = mockk<BookDao>(relaxed = true)
    private val savedWordDao = mockk<SavedWordDao>(relaxed = true)
    private val bookmarkDao = mockk<BookmarkDao>(relaxed = true)
    private val repo = DefaultLibraryRepository(dao, savedWordDao, bookmarkDao, FakeActivityRepository())

    @Test fun observeBooks_maps_entities_to_domain() = runTest {
        coEvery { dao.observeBooks() } returns flowOf(
            listOf(BookEntity(7, "Dune", "Herbert", null, "/d.epub", 1L, null)),
        )
        repo.observeBooks().test {
            val books = awaitItem()
            assertEquals(1, books.size)
            assertEquals(7L, books.first().id)
            assertEquals("Dune", books.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun addBook_delegates_to_dao() = runTest {
        coEvery { dao.upsertBook(any()) } returns 9L
        val id = repo.addBook(Book(0, "Dune", null, null, "/d.epub", 1L, null))
        assertEquals(9L, id)
        coVerify { dao.upsertBook(any()) }
    }
}
