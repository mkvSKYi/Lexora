package com.reader.feature.translation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Horizontal padding applied to the sheet body. */
private val SHEET_HORIZONTAL_PADDING = 24.dp

/** Vertical padding applied to the sheet body. */
private val SHEET_VERTICAL_PADDING = 16.dp

/** Minimum height of the loading state, so the sheet has presence while looking up. */
private val LOADING_MIN_HEIGHT = 160.dp

/** Vertical spacing between sections of the entry layout. */
private val SECTION_SPACING = 12.dp

/** Definitions shown before "Show more" expands the rest, so a word with many senses stays compact. */
private const val COLLAPSED_DEFINITIONS = 5

/**
 * Modal bottom sheet that renders a [WordLookupState] for a tapped word.
 *
 * Shows a spinner while [WordLookupState.Loading], a rich dictionary [WordLookupState.Entry]
 * (headword, IPA, part of speech, translations and definitions), a plain machine
 * [WordLookupState.Machine] translation, or an [WordLookupState.Error] message. The Save button
 * invokes [onSave] with the headword and the best available translation/definition; it is omitted
 * when there is nothing to save. Dismissing the sheet invokes [onDismiss].
 *
 * @param showSave whether to render the Save button (default true). Pass false for a read-only
 *   sheet — e.g. opening an already-saved word from Saved Words just to view its definitions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDictionarySheet(
    state: WordLookupState,
    onSave: (term: String, translation: String) -> Unit,
    onDismiss: () -> Unit,
    showSave: Boolean = true,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (state) {
            WordLookupState.Loading -> LoadingContent()
            is WordLookupState.Entry -> EntryContent(state, onSave, showSave)
            is WordLookupState.Machine -> MachineContent(state, onSave, showSave)
            is WordLookupState.Error -> ErrorContent(state)
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LOADING_MIN_HEIGHT),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Looking up…",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryContent(
    state: WordLookupState.Entry,
    onSave: (term: String, translation: String) -> Unit,
    showSave: Boolean,
) {
    val saveValue = state.translations.firstOrNull()
        ?: state.machineTranslation
        ?: state.definitions.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SHEET_HORIZONTAL_PADDING, vertical = SHEET_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Text(
            text = state.word,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (state.ipa != null || state.partOfSpeech != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.ipa?.let { ipa ->
                    Text(
                        text = ipa,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.partOfSpeech?.let { partOfSpeech ->
                    Text(
                        text = partOfSpeech,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // A single Ukrainian translation: the dictionary's when present, otherwise the ML Kit one.
        when {
            state.translations.isNotEmpty() -> {
                SectionLabel("Translation")
                Text(
                    text = state.translations.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            state.translationPending -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Translating…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.machineTranslation != null -> {
                SectionLabel("Translation")
                Text(
                    text = state.machineTranslation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (state.definitions.isNotEmpty()) {
            var expanded by remember(state.word) { mutableStateOf(false) }
            val shown = if (expanded) state.definitions else state.definitions.take(COLLAPSED_DEFINITIONS)
            SectionLabel("Definitions")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEachIndexed { index, definition ->
                    Text(
                        text = "${index + 1}. $definition",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                val remaining = state.definitions.size - COLLAPSED_DEFINITIONS
                if (remaining > 0) {
                    Text(
                        text = if (expanded) "Show less" else "Show $remaining more",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }

        if (showSave && saveValue != null) {
            Button(onClick = { onSave(state.word, saveValue) }) {
                Text(text = "Save")
            }
        }
    }
}

@Composable
private fun MachineContent(
    state: WordLookupState.Machine,
    onSave: (term: String, translation: String) -> Unit,
    showSave: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SHEET_HORIZONTAL_PADDING, vertical = SHEET_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Text(
            text = state.word,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.translation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showSave) {
            Button(onClick = { onSave(state.word, state.translation) }) {
                Text(text = "Save")
            }
        }
    }
}

@Composable
private fun ErrorContent(state: WordLookupState.Error) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SHEET_HORIZONTAL_PADDING, vertical = SHEET_VERTICAL_PADDING),
    ) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
