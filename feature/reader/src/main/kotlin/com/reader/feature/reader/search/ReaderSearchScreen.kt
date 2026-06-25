package com.reader.feature.reader.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

/** Aurora accent used to highlight the matched term inside each result snippet. */
private val ACCENT = Color(0xFF9B8CFF)

/**
 * Full-screen in-book search overlay. Pure rendering: it takes the current [state] and emits
 * user intents through [onQuery], [onResultClick], and [onClose].
 *
 * @param state the current search state to render.
 * @param onQuery invoked with the submitted query when the user triggers the IME search action.
 * @param onResultClick invoked with the tapped result.
 * @param onClose invoked when the user dismisses the overlay.
 */
@Composable
fun ReaderSearchScreen(
    state: BookSearchState,
    onQuery: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onClose: () -> Unit,
) {
    // System back closes the search overlay instead of falling through to the reader's back
    // (which would navigate out of the book).
    androidx.activity.compose.BackHandler { onClose() }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            var text by remember { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text("Search in book") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onQuery(text) }),
                )
            }

            when (state) {
                is BookSearchState.Idle -> CenteredMessage("Type to search")

                is BookSearchState.Searching -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    ResultsList(results = state.results, onResultClick = onResultClick)
                }

                is BookSearchState.Results ->
                    ResultsList(results = state.results, onResultClick = onResultClick)

                is BookSearchState.Empty -> CenteredMessage("No results")

                is BookSearchState.Error ->
                    CenteredMessage("Search isn't available for this book.")
            }
        }
    }
}

/** Scrollable list of search hits; reused by the Searching and Results states. */
@Composable
private fun ResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results) { result ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResultClick(result) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = snippetOf(result),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                result.chapterTitle?.let { chapter ->
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Builds "…before **highlight** after…" with the matched term bold + accent-colored. */
private fun snippetOf(result: SearchResult): AnnotatedString = buildAnnotatedString {
    append(result.before)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ACCENT)) {
        append(result.highlight)
    }
    append(result.after)
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
