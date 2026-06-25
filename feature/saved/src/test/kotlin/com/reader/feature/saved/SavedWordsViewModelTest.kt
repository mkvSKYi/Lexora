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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedWordsViewModelTest {
    private val repo = mockk<SavedWordsRepository>(relaxed = true)
    private fun word(id: Long, learned: Boolean) =
        SavedWord(id, "t$id", "п$id", null, 1, "Book", id, learned)

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun content_carries_filtered_list_and_full_counts() = runTest {
        every { repo.observe() } returns flowOf(listOf(word(1, true), word(2, false), word(3, true)))
        val vm = SavedWordsViewModel(repo)
        vm.uiState.test {
            assertEquals(SavedWordsUiState.Loading, awaitItem())
            val all = awaitItem() as SavedWordsUiState.Content
            assertEquals(3, all.words.size)
            assertEquals(2, all.learnedCount)
            assertEquals(3, all.totalCount)
            vm.setFilter(SavedWordsFilter.LEARNED)
            val learned = awaitItem() as SavedWordsUiState.Content
            assertEquals(listOf(1L, 3L), learned.words.map { it.id })
            assertEquals(2, learned.learnedCount) // counts from the FULL list
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun toggleLearned_delegates_to_repository() = runTest {
        every { repo.observe() } returns flowOf(emptyList())
        val vm = SavedWordsViewModel(repo)
        vm.toggleLearned(7, true)
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.markLearned(7, true) }
    }
}
