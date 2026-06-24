package com.reader.feature.reader.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Base horizontal padding for a TOC row; each depth level adds [INDENT_PER_DEPTH]. */
private val ROW_BASE_PADDING = 16.dp

/** Extra horizontal inset added per nesting [TocEntry.depth] level. */
private val INDENT_PER_DEPTH = 16.dp

/** Minimum height of the empty "No chapters" state, so the sheet has presence even when empty. */
private val EMPTY_STATE_MIN_HEIGHT = 160.dp

/**
 * Bottom sheet listing the publication's table of contents.
 *
 * Renders [entries] in a [LazyColumn], each row indented by its [TocEntry.depth] and the entry
 * whose [TocEntry.href] equals [currentHref] visually highlighted (tinted background + bold) so
 * the reader can see where they are. Tapping a row invokes [onEntryClick]; an empty [entries] list
 * shows a centered "No chapters" message. Dismissing the sheet invokes [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocSheet(
    entries: List<TocEntry>,
    currentHref: String?,
    onEntryClick: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = EMPTY_STATE_MIN_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No chapters",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn {
                items(entries) { entry ->
                    TocRow(
                        entry = entry,
                        isCurrent = entry.href == currentHref,
                        onClick = { onEntryClick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TocRow(
    entry: TocEntry,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val startPadding = ROW_BASE_PADDING + INDENT_PER_DEPTH * entry.depth
    val background =
        if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val textColor =
        if (isCurrent) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Text(
        text = entry.title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        color = textColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background)
            .padding(start = startPadding, end = ROW_BASE_PADDING, top = 12.dp, bottom = 12.dp),
    )
}
