package com.reader.feature.reader.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.core.data.model.Bookmark
import kotlin.math.roundToInt

/** Base horizontal padding for a TOC row; each depth level adds [INDENT_PER_DEPTH]. */
private val ROW_BASE_PADDING = 16.dp

/** Extra horizontal inset added per nesting [TocEntry.depth] level. */
private val INDENT_PER_DEPTH = 16.dp

/** Minimum height of the empty "No chapters" state, so the sheet has presence even when empty. */
private val EMPTY_STATE_MIN_HEIGHT = 160.dp

/** Maximum height of the bookmarks list before it scrolls within the sheet. */
private val BOOKMARKS_LIST_MAX_HEIGHT = 480.dp

/**
 * Bottom sheet listing the publication's table of contents and the reader's bookmarks.
 *
 * A [TabRow] switches between "Contents" (the [entries] in a [LazyColumn], each row indented by its
 * [TocEntry.depth] and the entry whose [TocEntry.href] equals [currentHref] highlighted) and
 * "Bookmarks" ([bookmarks] as swipe-to-delete rows). Tapping a TOC row invokes [onEntryClick];
 * tapping a bookmark invokes [onBookmarkClick]; swiping a bookmark invokes [onBookmarkDelete].
 * Dismissing the sheet invokes [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTocSheet(
    entries: List<TocEntry>,
    currentHref: String?,
    onEntryClick: (TocEntry) -> Unit,
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var tab by remember { mutableStateOf(0) }
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Contents") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Bookmarks") },
            )
        }
        when (tab) {
            0 -> TocList(entries, currentHref, onEntryClick)
            else -> BookmarksList(bookmarks, onBookmarkClick, onBookmarkDelete)
        }
    }
}

@Composable
private fun TocList(
    entries: List<TocEntry>,
    currentHref: String?,
    onEntryClick: (TocEntry) -> Unit,
) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksList(
    bookmarks: List<Bookmark>,
    onClick: (Bookmark) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EMPTY_STATE_MIN_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No bookmarks yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = BOOKMARKS_LIST_MAX_HEIGHT)) {
        items(bookmarks, key = { it.id }) { bm ->
            val dismiss = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onDelete(bm.id)
                        true
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(
                state = dismiss,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onClick(bm) }
                        .padding(horizontal = ROW_BASE_PADDING, vertical = 12.dp),
                ) {
                    Text(
                        text = bm.chapterTitle ?: "Bookmark",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${(bm.totalProgression * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .then(
                if (isCurrent) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                },
            )
            .padding(start = startPadding, end = ROW_BASE_PADDING, top = 12.dp, bottom = 12.dp),
    )
}
