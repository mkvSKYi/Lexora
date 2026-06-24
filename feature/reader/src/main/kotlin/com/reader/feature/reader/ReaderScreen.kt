package com.reader.feature.reader

import android.graphics.RectF
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.feature.translation.TranslationPopover
import com.reader.feature.translation.TranslationViewModel
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
                    epubPreferences = viewModel.epubPreferences,
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onSelection = onSelection,
                )
            }
        }
    }
}

@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
@Composable
private fun EpubReader(
    bookId: Long,
    state: ReaderUiState.Ready,
    epubPreferences: kotlinx.coroutines.flow.StateFlow<org.readium.r2.navigator.epub.EpubPreferences>,
    onLocatorChanged: (Long, org.readium.r2.shared.publication.Locator) -> Unit,
    onSelection: (SelectionEvent) -> Unit,
) {
    val translationVm: TranslationViewModel = hiltViewModel()
    val popup by translationVm.popupState.collectAsStateWithLifecycle()

    // Anchor for the popover: the tapped word's bounds in navigator-view pixels.
    var anchorRect by remember { mutableStateOf<RectF?>(null) }

    // Forward selection events to the host while also driving translation + anchoring.
    val currentOnSelection by rememberUpdatedState(onSelection)
    val onSelectionInternal: (SelectionEvent) -> Unit = remember {
        { event ->
            anchorRect = event.rectInView
            translationVm.onTextSelected(event.text)
            currentOnSelection(event)
        }
    }

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
                epubPreferences = epubPreferences,
                onLocatorChanged = { locator ->
                    // A page turn / new locator invalidates the anchored popover.
                    if (anchorRect != null) {
                        anchorRect = null
                        translationVm.dismiss()
                    }
                    onLocatorChanged(bookId, locator)
                },
                onSelection = { event -> onSelectionInternal(event) },
            ),
        )
    }

    DisposableEffect(sessionId) {
        onDispose { ReaderNavigatorHost.unregister(sessionId) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidFragment<EpubReaderFragment>(
            modifier = Modifier.fillMaxSize(),
            arguments = EpubReaderFragment.argsFor(sessionId),
        )

        val currentPopup = popup
        val rect = anchorRect
        if (currentPopup != null && rect != null) {
            val density = LocalDensity.current
            // rectInView is in navigator-view pixels, which align with the pixel space the
            // Popup positions against (parent bounds + window-relative). Anchor the popover
            // just below the word, horizontally centered, then clamp on-screen.
            val positionProvider = remember(rect, density) {
                WordAnchorPositionProvider(
                    anchorPx = rect,
                    gapPx = with(density) { GAP_DP.dp.toPx() },
                )
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = {
                    anchorRect = null
                    translationVm.dismiss()
                },
            ) {
                TranslationPopover(
                    state = currentPopup,
                    onDismiss = {
                        anchorRect = null
                        translationVm.dismiss()
                    },
                )
            }
        }
    }
}

private const val GAP_DP = 8

/**
 * Positions the translation popover just below the tapped word (in navigator-view pixels),
 * horizontally centered on it, falling back to above the word when there is no room below.
 * The result is clamped to the window so the popover never spills off-screen.
 */
private class WordAnchorPositionProvider(
    private val anchorPx: RectF,
    private val gapPx: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset {
        val anchorCenterX = ((anchorPx.left + anchorPx.right) / 2f).toInt()
        var x = anchorCenterX - popupContentSize.width / 2
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        val below = (anchorPx.bottom + gapPx).toInt()
        val above = (anchorPx.top - gapPx).toInt() - popupContentSize.height
        var y = if (below + popupContentSize.height <= windowSize.height) below else above
        y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))

        return IntOffset(x, y)
    }
}
