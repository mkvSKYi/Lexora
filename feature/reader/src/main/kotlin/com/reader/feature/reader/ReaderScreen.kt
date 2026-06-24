package com.reader.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.readium.r2.navigator.epub.EpubNavigatorFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
    onSelection: (SelectionEvent) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) { viewModel.load(bookId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                is ReaderUiState.Loading -> CircularProgressIndicator()

                is ReaderUiState.Error -> Text(state.message)

                is ReaderUiState.Ready -> EpubReader(
                    bookId = bookId,
                    state = state,
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onSelection = onSelection,
                )
            }
        }
    }
}

@Composable
private fun EpubReader(
    bookId: Long,
    state: ReaderUiState.Ready,
    onLocatorChanged: (Long, org.readium.r2.shared.publication.Locator) -> Unit,
    onSelection: (SelectionEvent) -> Unit,
) {
    // Keep the latest onSelection without re-registering the session on each recomposition.
    val currentOnSelection by rememberUpdatedState(onSelection)

    // Register the navigator hand-off session for the lifetime of this composition.
    val sessionId = remember(state.publication) {
        val factory = EpubNavigatorFactory(
            publication = state.publication,
            configuration = EpubNavigatorFactory.Configuration(),
        )
        ReaderNavigatorHost.register(
            ReaderNavigatorHost.Session(
                navigatorFactory = factory,
                initialLocator = state.initialLocator,
                onLocatorChanged = { locator -> onLocatorChanged(bookId, locator) },
                onSelection = { event -> currentOnSelection(event) },
            ),
        )
    }

    DisposableEffect(sessionId) {
        onDispose { ReaderNavigatorHost.unregister(sessionId) }
    }

    AndroidFragment<EpubReaderFragment>(
        modifier = Modifier.fillMaxSize(),
        arguments = EpubReaderFragment.argsFor(sessionId),
    )
}
