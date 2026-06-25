package com.reader.feature.saved

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reader.core.data.model.SavedWord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class SavedWordsContentTest {

    @get:Rule val composeRule = createComposeRule()

    private fun word(id: Long, term: String, learned: Boolean) =
        SavedWord(id, term, "переклад", null, 1, "Book", 0, learned = learned)

    private fun content(words: List<SavedWord>) = SavedWordsUiState.Content(
        words = words,
        learnedCount = words.count { it.learned },
        totalCount = words.size,
        dueCount = 0,
    )

    @Test fun renders_words_filters_and_stats() {
        composeRule.setContent {
            MaterialTheme {
                SavedWordsContent(
                    uiState = content(listOf(word(1, "ephemeral", false), word(2, "serendipity", true))),
                    filter = SavedWordsFilter.ALL,
                    celebrateKey = 0,
                    onBack = {}, onStartReview = {}, onWordTap = {},
                    onSetFilter = {}, onToggleLearned = { _, _ -> }, onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("ephemeral").assertIsDisplayed()
        composeRule.onNodeWithText("serendipity").assertIsDisplayed()
        composeRule.onNodeWithText("of 2 learned").assertIsDisplayed()
        composeRule.onNodeWithText("Learning").assertIsDisplayed()
        composeRule.onNodeWithText("Learned").assertIsDisplayed()
    }

    @Test fun toggling_a_learning_word_emits_learned_true() {
        var toggled: Pair<Long, Boolean>? = null
        composeRule.setContent {
            MaterialTheme {
                SavedWordsContent(
                    uiState = content(listOf(word(7, "ephemeral", false))),
                    filter = SavedWordsFilter.ALL,
                    celebrateKey = 0,
                    onBack = {}, onStartReview = {}, onWordTap = {},
                    onSetFilter = {}, onToggleLearned = { id, learned -> toggled = id to learned }, onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mark as learned").performClick()
        assertEquals(7L to true, toggled)
    }

    @Test fun empty_state_prompts_to_save_words() {
        composeRule.setContent {
            MaterialTheme {
                SavedWordsContent(
                    uiState = content(emptyList()),
                    filter = SavedWordsFilter.ALL,
                    celebrateKey = 0,
                    onBack = {}, onStartReview = {}, onWordTap = {},
                    onSetFilter = {}, onToggleLearned = { _, _ -> }, onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("No saved words yet").assertIsDisplayed()
    }
}
