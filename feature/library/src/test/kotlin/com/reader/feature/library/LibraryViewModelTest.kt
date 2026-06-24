package com.reader.feature.library

import app.cash.turbine.test
import com.reader.core.data.LibraryRepository
import com.reader.core.data.imports.EpubImporter
import com.reader.core.data.model.Book
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryViewModelTest {
    private val repo = mockk<LibraryRepository>(relaxed = true)
    private val importer = mockk<EpubImporter>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher())

    @Test fun emits_content_from_repository() = runTest {
        every { repo.observeBooks() } returns flowOf(
            listOf(Book(1, "Dune", "Herbert", null, "/d.epub", 1L, null)),
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
}
