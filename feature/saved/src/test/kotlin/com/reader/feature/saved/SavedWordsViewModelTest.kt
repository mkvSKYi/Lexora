package com.reader.feature.saved

import app.cash.turbine.test
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.model.SavedWord
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
class SavedWordsViewModelTest {
    private val repo = mockk<SavedWordsRepository>(relaxed = true)
    @Before fun s() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun t() = Dispatchers.resetMain()

    @Test fun emits_content_from_repo() = runTest {
        every { repo.observe() } returns flowOf(
            listOf(SavedWord(1, "dog", "собака", null, 1, "Dune", 100, learned = false)),
        )
        val vm = SavedWordsViewModel(repo)
        vm.uiState.test {
            assertEquals(SavedWordsUiState.Loading, awaitItem())
            val c = awaitItem()
            assertTrue(c is SavedWordsUiState.Content)
            assertEquals(1, (c as SavedWordsUiState.Content).words.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun delete_delegates() = runTest {
        every { repo.observe() } returns flowOf(emptyList())
        val vm = SavedWordsViewModel(repo)
        vm.delete(7)
        advanceUntilIdle()
        coVerify { repo.delete(7) }
    }
}
