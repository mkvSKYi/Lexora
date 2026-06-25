package com.reader.feature.translation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WordDictionarySheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun entry_renders_headword_ipa_part_of_speech_and_a_definition() {
        composeRule.setContent {
            MaterialTheme {
                WordDictionarySheet(
                    state = WordLookupState.Entry(
                        word = "cat",
                        ipa = "/kæt/",
                        partOfSpeech = "noun",
                        definitions = listOf("a small domesticated carnivore"),
                        translations = listOf("кіт"),
                    ),
                    onSave = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("/kæt/").assertIsDisplayed()
        composeRule.onNodeWithText("noun").assertIsDisplayed()
        composeRule.onNodeWithText("кіт").assertIsDisplayed()
        // The definition line sits at the bottom of the partially-expanded sheet, so it may be
        // below the visible viewport in Robolectric; fetching its node proves it rendered.
        composeRule.onNodeWithText("a small domesticated carnivore", substring = true)
            .fetchSemanticsNode()
    }

    @Test
    fun entry_save_button_invokes_callback_with_word_and_translation() {
        var savedTerm: String? = null
        var savedTranslation: String? = null

        composeRule.setContent {
            MaterialTheme {
                WordDictionarySheet(
                    state = WordLookupState.Entry(
                        word = "cat",
                        ipa = null,
                        partOfSpeech = null,
                        definitions = emptyList(),
                        translations = listOf("кіт"),
                    ),
                    onSave = { term, translation ->
                        savedTerm = term
                        savedTranslation = translation
                    },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save").performClick()

        assertEquals("cat", savedTerm)
        assertEquals("кіт", savedTranslation)
    }

    @Test
    fun entry_with_pending_translation_renders_translating_indicator() {
        composeRule.setContent {
            MaterialTheme {
                WordDictionarySheet(
                    state = WordLookupState.Entry(
                        word = "cat",
                        ipa = null,
                        partOfSpeech = null,
                        definitions = emptyList(),
                        translations = emptyList(),
                        translationPending = true,
                    ),
                    onSave = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Translating…").assertIsDisplayed()
    }

    @Test
    fun loading_state_renders_looking_up_message() {
        composeRule.setContent {
            MaterialTheme {
                WordDictionarySheet(
                    state = WordLookupState.Loading,
                    onSave = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Looking up…").assertIsDisplayed()
    }

    @Test
    fun speaker_control_renders_and_invokes_callback_when_speech_available() {
        var spokenWord: String? = null

        composeRule.setContent {
            MaterialTheme {
                WordDictionarySheet(
                    state = WordLookupState.Entry(
                        word = "cat",
                        ipa = null,
                        partOfSpeech = null,
                        definitions = emptyList(),
                        translations = listOf("кіт"),
                    ),
                    onSave = { _, _ -> },
                    onDismiss = {},
                    canSpeak = true,
                    onSpeak = { spokenWord = it },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Pronounce").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pronounce").performClick()
        assertEquals("cat", spokenWord)
    }

    @Test
    fun machine_state_renders_word_and_translation_and_saves() {
        var savedTerm: String? = null
        var savedTranslation: String? = null

        composeRule.setContent {
            MaterialTheme {
                WordDictionarySheet(
                    state = WordLookupState.Machine(word = "cat", translation = "кіт"),
                    onSave = { term, translation ->
                        savedTerm = term
                        savedTranslation = translation
                    },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("кіт").assertIsDisplayed()

        composeRule.onNodeWithText("Save").performClick()
        assertEquals("cat", savedTerm)
        assertEquals("кіт", savedTranslation)
    }
}
