package com.reader.feature.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class DashboardScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun content(
        streak: Long = 0,
        words: WordStats = WordStats(0, 0, 0),
        books: BookStats = BookStats(0, 0),
        hasActivity: Boolean = true,
        todayActions: Int = 0,
        dailyGoal: Int = 5,
    ) = DashboardUiState.Content(
        streak = streak,
        heatmap = (0 until DashboardViewModel.HEATMAP_DAYS).map { HeatCell(it.toLong(), 0, isFuture = false) },
        words = words,
        books = books,
        hasActivity = hasActivity,
        todayActions = todayActions,
        dailyGoal = dailyGoal,
    )

    @Test fun renders_streak_and_stats() {
        composeRule.setContent {
            MaterialTheme {
                DashboardContent(
                    state = content(streak = 5, words = WordStats(total = 12, learned = 4, due = 3)),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("day streak").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("Review 3 now").assertIsDisplayed()
    }

    @Test fun review_button_enabled_and_fires_when_words_are_due() {
        var reviewed = false
        composeRule.setContent {
            MaterialTheme {
                DashboardContent(
                    state = content(words = WordStats(total = 5, learned = 0, due = 2)),
                    onStartReview = { reviewed = true },
                )
            }
        }

        composeRule.onNodeWithText("Review 2 now").assertIsEnabled()
        composeRule.onNodeWithText("Review 2 now").performClick()
        assertEquals(true, reviewed)
    }

    @Test fun review_button_disabled_when_nothing_due() {
        composeRule.setContent {
            MaterialTheme {
                DashboardContent(
                    state = content(words = WordStats(total = 5, learned = 5, due = 0)),
                    onStartReview = {},
                )
            }
        }

        composeRule.onNodeWithText("Nothing to review").assertIsNotEnabled()
    }

    @Test fun daily_goal_card_shows_progress_then_completion() {
        composeRule.setContent {
            MaterialTheme {
                DashboardContent(state = content(todayActions = 2, dailyGoal = 5), onStartReview = {})
            }
        }
        composeRule.onNodeWithText("DAILY GOAL").assertIsDisplayed()
        composeRule.onNodeWithText("2/5").assertIsDisplayed()
        composeRule.onNodeWithText("3 more to go").assertIsDisplayed()
    }

    @Test fun daily_goal_card_announces_completion() {
        composeRule.setContent {
            MaterialTheme {
                DashboardContent(state = content(todayActions = 5, dailyGoal = 5), onStartReview = {})
            }
        }
        composeRule.onNodeWithText("Goal complete!").assertIsDisplayed()
    }

    @Test fun shows_first_run_empty_state_when_no_activity() {
        composeRule.setContent {
            MaterialTheme {
                DashboardContent(state = content(hasActivity = false), onStartReview = {})
            }
        }

        composeRule.onNodeWithText("Start your streak").assertIsDisplayed()
    }
}
