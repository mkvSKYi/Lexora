package com.reader.feature.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PopoverShape
    @Composable get() = MaterialTheme.shapes.medium

/**
 * Renders the content of the tap-to-translate popover for a given [state].
 *
 * This composable only draws the card content sized to its content; positioning
 * and outside-tap dismissal are the responsibility of the caller (the reader wraps
 * this in a Compose `Popup`). [onDismiss] is provided for an optional close
 * affordance but need not be wired to a button here.
 */
@Composable
fun TranslationPopover(
    state: TranslationPopupState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(min = 160.dp, max = 320.dp),
        shape = PopoverShape,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (state) {
            TranslationPopupState.Loading -> LoadingContent()
            is TranslationPopupState.Result -> ResultContent(state, onSave)
            is TranslationPopupState.Error -> ErrorContent(state)
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Translating…",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultContent(state: TranslationPopupState.Result, onSave: () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = state.source,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.translation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSave) {
                Text(text = "Save")
            }
        }
    }
}

@Composable
private fun ErrorContent(state: TranslationPopupState.Error) {
    Text(
        text = state.message,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
