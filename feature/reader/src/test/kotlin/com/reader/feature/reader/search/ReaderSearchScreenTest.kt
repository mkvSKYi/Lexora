package com.reader.feature.reader.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderSearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idle_state_prompts_the_user_to_type() {
        composeRule.setContent {
            ReaderSearchScreen(
                state = BookSearchState.Idle,
                onQuery = {},
                onResultClick = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithText("Type to search").assertIsDisplayed()
    }

    @Test
    fun empty_state_shows_no_results() {
        composeRule.setContent {
            ReaderSearchScreen(
                state = BookSearchState.Empty,
                onQuery = {},
                onResultClick = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithText("No results").assertIsDisplayed()
    }

    @Test
    fun results_render_snippets_and_a_tap_emits_the_result() {
        val result = searchResult(before = "The cat ", highlight = "sat", after = " on the mat")
        var clicked: SearchResult? = null

        composeRule.setContent {
            ReaderSearchScreen(
                state = BookSearchState.Results(listOf(result)),
                onQuery = {},
                onResultClick = { clicked = it },
                onClose = {},
            )
        }

        // The highlighted term is rendered inside the snippet.
        composeRule.onNodeWithText("sat", substring = true).assertIsDisplayed()
        // Tapping the row emits exactly that result.
        composeRule.onNodeWithText("sat", substring = true).performClick()
        assertEquals(result, clicked)
    }

    private fun searchResult(before: String, highlight: String, after: String) = SearchResult(
        locator = Locator(
            href = Url("ch1.html")!!,
            mediaType = MediaType.XHTML,
            text = Locator.Text(before = before, highlight = highlight, after = after),
        ),
        before = before,
        highlight = highlight,
        after = after,
        chapterTitle = "Chapter 1",
    )
}
