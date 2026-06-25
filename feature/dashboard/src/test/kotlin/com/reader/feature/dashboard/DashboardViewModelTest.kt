package com.reader.feature.dashboard

import com.reader.core.data.LibraryRepository
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.activity.ActivityRepository
import com.reader.core.data.activity.TodayProvider
import com.reader.core.data.model.Book
import com.reader.core.data.model.BookWithProgress
import com.reader.core.data.model.DailyActivity
import com.reader.core.data.model.SavedWord
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = 20_000L

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        activity: List<DailyActivity> = emptyList(),
        words: List<SavedWord> = emptyList(),
        books: List<BookWithProgress> = emptyList(),
    ): DashboardViewModel {
        val activityRepo = mockk<ActivityRepository> {
            every { observeSince(any()) } returns flowOf(activity)
        }
        val wordsRepo = mockk<SavedWordsRepository> { every { observe() } returns flowOf(words) }
        val libraryRepo = mockk<LibraryRepository> {
            every { observeBooksWithProgress() } returns flowOf(books)
        }
        return DashboardViewModel(activityRepo, wordsRepo, libraryRepo, TodayProvider { today })
    }

    private fun kotlinx.coroutines.test.TestScope.content(vm: DashboardViewModel): DashboardUiState.Content {
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        advanceUntilIdle()
        return vm.state.value as DashboardUiState.Content
    }

    @Test fun streak_counts_consecutive_active_days_ending_today() = runTest {
        val activity = listOf(
            DailyActivity(today, readingActive = true, wordsSaved = 0, wordsReviewed = 0),
            DailyActivity(today - 1, readingActive = false, wordsSaved = 2, wordsReviewed = 0),
        )
        val state = content(viewModel(activity = activity))
        assertEquals(2L, state.streak)
        assertEquals(true, state.hasActivity)
    }

    @Test fun heatmap_has_thirteen_weeks_of_cells() = runTest {
        val state = content(viewModel())
        assertEquals(DashboardViewModel.HEATMAP_DAYS, state.heatmap.size)
        assertEquals(false, state.hasActivity)
        assertEquals(0L, state.streak)
    }

    @Test fun word_stats_split_total_learned_and_due() = runTest {
        val words = listOf(
            word(id = 1, learned = false, dueAt = 1L),            // due (past)
            word(id = 2, learned = false, dueAt = Long.MAX_VALUE), // not due
            word(id = 3, learned = true, dueAt = 1L),             // learned
        )
        val state = content(viewModel(words = words))
        assertEquals(3, state.words.total)
        assertEquals(1, state.words.learned)
        assertEquals(1, state.words.due)
    }

    @Test fun book_stats_threshold_in_progress_versus_finished() = runTest {
        val books = listOf(
            bookAt(0.0),  // untouched — neither
            bookAt(0.4),  // in progress
            bookAt(0.985), // in progress (below 0.99)
            bookAt(0.99),  // finished
            bookAt(1.0),   // finished
        )
        val state = content(viewModel(books = books))
        assertEquals(2, state.books.inProgress)
        assertEquals(2, state.books.finished)
    }

    private fun word(id: Long, learned: Boolean, dueAt: Long) =
        SavedWord(id, "term$id", "переклад", null, 1, "Book", 0, learned = learned, dueAt = dueAt)

    private fun bookAt(percent: Double) = BookWithProgress(
        book = Book(0, "Book", null, null, "/b.epub", 0, null),
        percent = percent,
    )
}
