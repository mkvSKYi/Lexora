package com.reader.feature.saved.review

import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.model.SavedWord
import com.reader.core.data.review.ReviewGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class ReviewSessionViewModelTest {
    private fun word(id: Long) = SavedWord(id, "w$id", "п$id", null, 1, "B", 0, false)

    private class FakeRepo(private val due: List<SavedWord>) : SavedWordsRepository {
        override fun observe(): Flow<List<SavedWord>> = flowOf(emptyList())
        override suspend fun save(word: SavedWord) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun markLearned(id: Long, learned: Boolean) = error("unused")
        override suspend fun getDueWords(now: Long, limit: Int): List<SavedWord> = due.take(limit)
        override suspend fun applyReview(word: SavedWord, grade: ReviewGrade, now: Long): SavedWord =
            if (grade == ReviewGrade.AGAIN) word.copy(repetitions = 0)
            else word.copy(repetitions = word.repetitions + 1)
    }

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun loads_due_words_into_reviewing_state() = runTest {
        val vm = ReviewSessionViewModel(FakeRepo(listOf(word(1), word(2))), FakeXpRepository())
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is ReviewSessionUiState.Reviewing)
        assertEquals(2, (s as ReviewSessionUiState.Reviewing).total)
    }

    @Test fun good_advances_to_next_then_summary() = runTest {
        val vm = ReviewSessionViewModel(FakeRepo(listOf(word(1), word(2))), FakeXpRepository())
        advanceUntilIdle()
        vm.reveal(); vm.grade(ReviewGrade.GOOD); advanceUntilIdle()
        vm.reveal(); vm.grade(ReviewGrade.GOOD); advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is ReviewSessionUiState.Summary)
        assertEquals(2, (s as ReviewSessionUiState.Summary).reviewed)
    }

    @Test fun again_requeues_card() = runTest {
        val vm = ReviewSessionViewModel(FakeRepo(listOf(word(1))), FakeXpRepository())
        advanceUntilIdle()
        vm.reveal(); vm.grade(ReviewGrade.AGAIN); advanceUntilIdle()
        // still reviewing — the card came back
        assertTrue(vm.state.value is ReviewSessionUiState.Reviewing)
        vm.reveal(); vm.grade(ReviewGrade.GOOD); advanceUntilIdle()
        assertTrue(vm.state.value is ReviewSessionUiState.Summary)
    }

    @Test fun empty_due_shows_empty() = runTest {
        val vm = ReviewSessionViewModel(FakeRepo(emptyList()), FakeXpRepository())
        advanceUntilIdle()
        assertTrue(vm.state.value is ReviewSessionUiState.Empty)
    }

    @Test fun grading_a_card_grants_xp() = runTest {
        val xp = FakeXpRepository()
        val vm = ReviewSessionViewModel(FakeRepo(listOf(word(1), word(2))), xp)
        advanceUntilIdle()
        vm.reveal(); vm.grade(ReviewGrade.GOOD); advanceUntilIdle()
        assertEquals(com.reader.core.data.xp.LexoraXp.XP_PER_REVIEW, xp.total)
    }
}
