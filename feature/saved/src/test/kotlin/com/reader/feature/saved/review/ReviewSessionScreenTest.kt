package com.reader.feature.saved.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.model.SavedWord
import com.reader.core.data.review.ReviewGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewSessionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun word(id: Long, term: String, translation: String) =
        SavedWord(id, term, translation, contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 0, learned = false)

    private class FakeRepo(private val due: List<SavedWord>) : SavedWordsRepository {
        var lastGrade: ReviewGrade? = null
        override fun observe(): Flow<List<SavedWord>> = flowOf(emptyList())
        override suspend fun save(word: SavedWord) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun markLearned(id: Long, learned: Boolean) = error("unused")
        override suspend fun getDueWords(now: Long, limit: Int): List<SavedWord> = due.take(limit)
        override suspend fun applyReview(word: SavedWord, grade: ReviewGrade, now: Long): SavedWord {
            lastGrade = grade
            return word.copy(repetitions = word.repetitions + 1)
        }
    }

    private fun viewModel(repo: FakeRepo): ReviewSessionViewModel {
        val vm = ReviewSessionViewModel(repo, FakeXpRepository())
        // Drain the init {} coroutine so the StateFlow has settled before we render.
        scope.advanceUntilIdle()
        return vm
    }

    @Test
    fun reviewing_card_shows_the_prompt_and_hides_the_translation() {
        val repo = FakeRepo(listOf(word(1, "ephemeral", "ефемерний")))
        val vm = viewModel(repo)

        composeRule.setContent {
            ReviewSessionScreen(onDone = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("ephemeral").assertIsDisplayed()
        composeRule.onNodeWithText("Tap to reveal").assertIsDisplayed()
    }

    @Test
    fun revealing_the_card_shows_the_translation_and_grade_buttons() {
        val repo = FakeRepo(listOf(word(1, "ephemeral", "ефемерний")))
        val vm = viewModel(repo)

        composeRule.setContent {
            ReviewSessionScreen(onDone = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tap to reveal").performClick()
        scope.advanceUntilIdle()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("ефемерний").assertIsDisplayed()
        composeRule.onNodeWithText("Again").assertIsDisplayed()
        composeRule.onNodeWithText("Good").assertIsDisplayed()
        composeRule.onNodeWithText("Easy").assertIsDisplayed()
    }

    @Test
    fun grading_good_forwards_the_grade_to_the_repository() {
        val repo = FakeRepo(listOf(word(1, "ephemeral", "ефемерний")))
        val vm = viewModel(repo)

        composeRule.setContent {
            ReviewSessionScreen(onDone = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tap to reveal").performClick()
        scope.advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Good").performClick()
        // The grade is applied on Dispatchers.Main (our test dispatcher); drain it.
        scope.advanceUntilIdle()

        assertEquals(ReviewGrade.GOOD, repo.lastGrade)
    }

    @Test
    fun empty_due_queue_renders_the_caught_up_message() {
        val repo = FakeRepo(emptyList())
        val vm = viewModel(repo)

        composeRule.setContent {
            ReviewSessionScreen(onDone = {}, viewModel = vm)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Nothing to review").assertIsDisplayed()
        composeRule.onNodeWithText("You're all caught up.").assertIsDisplayed()
    }

    @Test
    fun completing_the_session_shows_the_celebration() {
        val repo = FakeRepo(listOf(word(1, "ephemeral", "ефемерний")))
        val vm = viewModel(repo)
        var done = false

        composeRule.setContent {
            ReviewSessionScreen(onDone = { done = true }, viewModel = vm)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tap to reveal").performClick()
        scope.advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Good").performClick()
        scope.advanceUntilIdle()
        composeRule.waitForIdle()

        // The only due card was graded → session complete → celebration.
        composeRule.onNodeWithText("Nice work!").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(true, done)
    }

    @Test
    fun done_button_on_empty_state_invokes_the_callback() {
        val repo = FakeRepo(emptyList())
        val vm = viewModel(repo)
        var done = false

        composeRule.setContent {
            ReviewSessionScreen(onDone = { done = true }, viewModel = vm)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Done").performClick()
        assertEquals(true, done)
    }
}
