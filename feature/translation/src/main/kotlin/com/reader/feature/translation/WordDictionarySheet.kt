package com.reader.feature.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Modal bottom sheet that renders a [WordLookupState] for a tapped word.
 *
 * Shows a spinner while [WordLookupState.Loading], a rich dictionary [WordLookupState.Entry]
 * (headword, IPA, part of speech, translations and definitions), a plain machine
 * [WordLookupState.Machine] translation, or an [WordLookupState.Error] message. The Save button
 * invokes [onSave] with the headword and the best available translation/definition; it is omitted
 * when there is nothing to save. Dismissing the sheet invokes [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDictionarySheet(
    state: WordLookupState,
    onSave: (term: String, translation: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (state) {
            WordLookupState.Loading -> LoadingContent()
            is WordLookupState.Entry -> EntryContent(state, onSave)
            is WordLookupState.Machine -> MachineContent(state, onSave)
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
) {
    val saveValue = state.translations.firstOrNull() ?: state.definitions.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
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

        if (state.translations.isNotEmpty()) {
            SectionLabel("Translations")
            Text(
                text = state.translations.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.definitions.isNotEmpty()) {
            SectionLabel("Definitions")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.definitions.forEachIndexed { index, definition ->
                    Text(
                        text = "${index + 1}. $definition",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (saveValue != null) {
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
        Button(onClick = { onSave(state.word, state.translation) }) {
            Text(text = "Save")
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
