package com.reader.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.core.data.model.Book
import com.reader.core.data.model.BookWithProgress
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.importErrors.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val pickEpub = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::importBook) }

    var menuBook by remember { mutableStateOf<Book?>(null) }
    var detailsBook by remember { mutableStateOf<Book?>(null) }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Aurora glow behind the header.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                AuroraAccentSoft.copy(alpha = 0.22f),
                                AuroraAccent.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            when (val state = uiState) {
                is LibraryUiState.Loading -> Unit
                is LibraryUiState.Content ->
                    if (state.books.isEmpty()) {
                        EmptyLibrary(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            onImport = { pickEpub.launch(arrayOf("application/epub+zip")) },
                        )
                    } else {
                        LibraryGrid(
                            books = state.books,
                            innerPadding = innerPadding,
                            onImport = { pickEpub.launch(arrayOf("application/epub+zip")) },
                            onBookClick = onBookClick,
                            onBookLongClick = { menuBook = it },
                        )
                    }
            }
        }
    }

    menuBook?.let { book ->
        BookContextMenuSheet(
            book = book,
            onDetails = { menuBook = null; detailsBook = book },
            onDelete = { menuBook = null; pendingDelete = book },
            onDismiss = { menuBook = null },
        )
    }
    detailsBook?.let { book ->
        val percent by produceState(initialValue = 0.0, book.id) { value = viewModel.progressPercent(book.id) }
        BookDetailsDialog(book = book, percent = percent, onDismiss = { detailsBook = null })
    }
    pendingDelete?.let { book ->
        DeleteBookDialog(
            book = book,
            onConfirm = { viewModel.deleteBook(book); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun LibraryGrid(
    books: List<BookWithProgress>,
    innerPadding: PaddingValues,
    onImport: () -> Unit,
    onBookClick: (Long) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    val hero = books.firstOrNull { it.book.lastOpenedAt != null }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(bookCount = books.size, onImport = onImport)
        }
        if (hero != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppearOnce(index = 0) {
                    ContinueReadingCard(item = hero, onClick = { onBookClick(hero.book.id) })
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Your books",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        booksIndexed(books) { index, item ->
            AppearOnce(index = index + 1) {
                BookCard(
                    item = item,
                    onClick = { onBookClick(item.book.id) },
                    onLongClick = { onBookLongClick(item.book) },
                )
            }
        }
    }
}

private fun LazyGridScope.booksIndexed(
    books: List<BookWithProgress>,
    itemContent: @Composable (index: Int, item: BookWithProgress) -> Unit,
) {
    items(count = books.size, key = { books[it].book.id }) { index ->
        itemContent(index, books[index])
    }
}

@Composable
private fun LibraryHeader(bookCount: Int, onImport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AuroraAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                tint = AuroraAccent,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row {
                com.reader.core.designsystem.motion.AnimatedCount(
                    target = bookCount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (bookCount == 1) " book" else " books",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledIconButton(
            onClick = onImport,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = AuroraAccentSoft,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Import EPUB")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(item: BookWithProgress, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box {
                BookCover(
                    book = item.book,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
                if (item.percent > 0.0) {
                    LinearProgressIndicator(
                        progress = { item.percent.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = AuroraAccent,
                        trackColor = Color.Black.copy(alpha = 0.35f),
                    )
                }
            }
        }
        Text(
            text = item.book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        item.book.author?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Fades + rises content once on first composition, staggered by [index]. */
@Composable
private fun AppearOnce(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 45L).coerceAtMost(350L))
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "appearAlpha")
    val offset by animateFloatAsState(if (shown) 0f else 28f, label = "appearOffset")
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha; translationY = offset }) {
        content()
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier, onImport: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AuroraAccentSoft, AuroraAccent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Import an EPUB to start reading and translating as you go.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Import EPUB")
        }
    }
}
