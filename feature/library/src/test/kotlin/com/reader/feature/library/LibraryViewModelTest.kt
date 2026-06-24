package com.reader.feature.library

import app.cash.turbine.test
import com.reader.core.data.LibraryRepository
import com.reader.core.data.imports.EpubImporter
import com.reader.core.data.model.Book
import com.reader.core.data.model.BookWithProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val repo = mockk<LibraryRepository>(relaxed = true)
    private val importer = mockk<EpubImporter>(relaxed = true)
    private val book = Book(7, "T", "A", null, "/x.epub", 1L, null)

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun teardown() = Dispatchers.resetMain()

    @Test fun emits_content_from_repository() = runTest {
        every { repo.observeBooksWithProgress() } returns flowOf(
            listOf(BookWithProgress(Book(1, "Dune", "Herbert", null, "/d.epub", 1L, null), 0.0)),
        )
        val vm = LibraryViewModel(repo, importer)
        vm.uiState.test {
            assertEquals(LibraryUiState.Loading, awaitItem())
            val content = awaitItem()
            assertTrue(content is LibraryUiState.Content)
            assertEquals(1, (content as LibraryUiState.Content).books.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun deleteBook_delegates_to_repository() = runTest {
        every { repo.observeBooksWithProgress() } returns flowOf(emptyList())
        val vm = LibraryViewModel(repo, importer)
        vm.deleteBook(book)
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.deleteBookCompletely(book) }
    }

    @Test fun progressPercent_delegates_to_repository() = runTest {
        every { repo.observeBooksWithProgress() } returns flowOf(emptyList())
        coEvery { repo.progressPercent(7) } returns 0.33
        val vm = LibraryViewModel(repo, importer)
        assertEquals(0.33, vm.progressPercent(7), 0.0001)
    }
}
