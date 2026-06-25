package com.reader.core.data

import app.cash.turbine.test
import com.reader.core.data.model.Bookmark
import com.reader.core.database.dao.BookmarkDao
import com.reader.core.database.entity.BookmarkEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarksRepositoryTest {
    private val dao = mockk<BookmarkDao>(relaxed = true)
    private val repo = DefaultBookmarksRepository(dao)

    @Test fun observe_maps_entities_to_domain() = runTest {
        coEvery { dao.observeForBook(7) } returns flowOf(
            listOf(BookmarkEntity(1, 7, "{}", "ch1.html", 0.5, 0.25, "Chapter 1", 100)),
        )
        repo.observe(7).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("ch1.html", list.first().href)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun add_and_delete_delegate() = runTest {
        repo.add(Bookmark(0, 7, "{}", "ch1.html", 0.5, 0.25, "Chapter 1", 100))
        coVerify { dao.insert(any()) }
        repo.delete(3)
        coVerify { dao.deleteById(3) }
    }
}
