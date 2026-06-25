package com.reader.feature.translation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranslationPopoverTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun result_renders_source_and_translation() {
        composeRule.setContent {
            MaterialTheme {
                TranslationPopover(
                    state = TranslationPopupState.Result(source = "cat", translation = "кіт"),
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("кіт").assertIsDisplayed()
    }

    @Test
    fun save_callback_fires_on_tap() {
        var saved = false

        composeRule.setContent {
            MaterialTheme {
                TranslationPopover(
                    state = TranslationPopupState.Result(source = "cat", translation = "кіт"),
                    onDismiss = {},
                    onSave = { saved = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save").performClick()
        assertTrue(saved)
    }

    @Test
    fun loading_state_renders_translating_message() {
        composeRule.setContent {
            MaterialTheme {
                TranslationPopover(
                    state = TranslationPopupState.Loading,
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Translating…").assertIsDisplayed()
    }

    @Test
    fun error_state_renders_message() {
        composeRule.setContent {
            MaterialTheme {
                TranslationPopover(
                    state = TranslationPopupState.Error(message = "No translation available"),
                    onDismiss = {},
                    onSave = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No translation available").assertIsDisplayed()
    }
}
