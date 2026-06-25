package com.reader.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RectF
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.feature.reader.brightness.BrightnessIndicator
import com.reader.feature.reader.brightness.nextBrightness
import com.reader.feature.reader.chrome.ReaderChrome
import com.reader.feature.reader.navigation.ReaderBottomBar
import com.reader.feature.reader.navigation.ReaderTocSheet
import com.reader.feature.reader.search.ReaderSearchScreen
import com.reader.feature.reader.settings.ReaderSettingsSheet
import com.reader.feature.translation.TranslationPopover
import com.reader.feature.translation.TranslationViewModel
import com.reader.feature.translation.WordDictionarySheet
import com.reader.feature.translation.WordLookupViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory

/** Delay before the reader chrome auto-hides while the reader is left untouched. */
private const val CHROME_AUTO_HIDE_MILLIS = 3_000L

/** Max opacity of the warmth (amber) overlay at full warmth. */
private const val MAX_WARMTH_ALPHA = 0.4f

/** The amber tint color used for the warmth overlay. */
private val WARMTH_COLOR = Color(0xFFFF8C00)

/** Aurora accent used by the reader chrome + progress hairline. */
internal val AURORA_ACCENT = Color(0xFF9B8CFF)

/** Width of the far-right edge strip that owns the brightness swipe (narrow, in the page margin). */
private val BRIGHTNESS_EDGE_WIDTH = 40.dp

@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
    onSelection: (SelectionEvent) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) { viewModel.load(bookId) }

    // Chrome shows on open, auto-hides after a few seconds, and re-reveals on a top-edge tap.
    var chromeVisible by remember { mutableStateOf(true) }

    // Appearance ("Aa") sheet toggle.
    var settingsVisible by remember { mutableStateOf(false) }

    // Table-of-contents sheet toggle.
    var tocVisible by remember { mutableStateOf(false) }

    // Full-screen in-book search overlay toggle.
    var searchVisible by remember { mutableStateOf(false) }
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

    // Brief "Saved" confirmation shown after a word is saved from the popover.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    // Right-side brightness swipe: the level to render in the indicator (null = hidden).
    var brightnessIndicator by remember { mutableStateOf<Float?>(null) }
    val brightnessScope = rememberCoroutineScope()
    var brightnessHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val currentChapterHref by viewModel.currentChapterHref.collectAsStateWithLifecycle()
    val currentProgression by viewModel.currentProgression.collectAsStateWithLifecycle()
    val currentChapterTitle by viewModel.currentChapterTitle.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentBookmarked.collectAsStateWithLifecycle()

    val epubPreferences by viewModel.epubPreferences.collectAsStateWithLifecycle()
    val brightness by viewModel.brightness.collectAsStateWithLifecycle()
    val warmth by viewModel.warmth.collectAsStateWithLifecycle()
    val highlightEnabled by viewModel.highlightEnabled.collectAsStateWithLifecycle()

    // Apply the brightness override to the host window while the reader is shown, restoring
    // the window's previous brightness when we leave (so the system value isn't left stuck).
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    val currentBrightness by rememberUpdatedState(brightness)
    DisposableEffect(window) {
        val attrs = window?.attributes
        val previousBrightness = attrs?.screenBrightness
        onDispose {
            if (window != null && attrs != null && previousBrightness != null) {
                attrs.screenBrightness = previousBrightness
                window.attributes = attrs
            }
        }
    }
    LaunchedEffect(window, brightness) {
        val attrs = window?.attributes ?: return@LaunchedEffect
        attrs.screenBrightness =
            currentBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
    }

    // Auto-hide: while visible, schedule a hide; each reveal restarts the timer.
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(CHROME_AUTO_HIDE_MILLIS)
            chromeVisible = false
        }
    }

    if (settingsVisible) {
        ReaderSettingsSheet(
            prefs = epubPreferences,
            onPrefsChange = viewModel::updateEpubPreferences,
            brightness = brightness,
            warmth = warmth,
            onBrightnessChange = viewModel::setBrightness,
            onWarmthChange = viewModel::setWarmth,
            highlightEnabled = highlightEnabled,
            onHighlightChange = viewModel::setHighlightEnabled,
            onDismiss = { settingsVisible = false },
        )
    }

    if (tocVisible) {
        ReaderTocSheet(
            entries = toc,
            currentHref = currentChapterHref,
            onEntryClick = { entry ->
                entry.locator?.let(viewModel::goTo)
                tocVisible = false
            },
            bookmarks = bookmarks,
            onBookmarkClick = { bm ->
                viewModel.jumpToBookmark(bm)
                tocVisible = false
            },
            onBookmarkDelete = viewModel::deleteBookmark,
            onDismiss = { tocVisible = false },
        )
    }

    ReaderChrome(
        visible = chromeVisible,
        bookmarked = isBookmarked,
        onBack = onBack,
        onToc = { tocVisible = true },
        onToggleBookmark = viewModel::toggleBookmark,
        onSearch = { searchVisible = true },
        onAa = { settingsVisible = true },
        onRevealStripTap = { chromeVisible = !chromeVisible },
        bottomBar = {
            ReaderBottomBar(
                progression = currentProgression,
                chapterTitle = currentChapterTitle,
                onSeek = viewModel::seekTo,
            )
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                is ReaderUiState.Loading -> CircularProgressIndicator()

                is ReaderUiState.Error -> Text(state.message)

                is ReaderUiState.Ready -> EpubReader(
                    bookId = bookId,
                    state = state,
                    epubPreferences = viewModel.epubPreferences,
                    highlight = viewModel.highlight,
                    navigateRequests = viewModel.navigateRequests,
                    onLocatorChanged = viewModel::onLocatorChanged,
                    onSelection = onSelection,
                    onSaveWord = { term, translation, context ->
                        viewModel.saveCurrentWord(term, translation, context)
                        snackbarScope.launch { snackbarHostState.showSnackbar("Saved") }
                    },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // Warmth (amber) tint drawn over the page. A plain Box with no clickable /
            // pointerInput so word-tap, long-press, and swipe still reach the navigator.
            if (warmth > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WARMTH_COLOR.copy(alpha = warmth * MAX_WARMTH_ALPHA)),
                )
            }

            // Always-on thin reading-progress hairline at the very bottom edge. Drawn before the
            // chrome bottom bar (which covers it when chrome is visible). A faint full-width track
            // makes the accent fill read as a progress bar rather than a stray mark at low progress.
            LinearProgressIndicator(
                progress = { currentProgression.coerceIn(0f, 1f) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = AURORA_ACCENT,
                trackColor = AURORA_ACCENT.copy(alpha = 0.16f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )

            // Brightness gesture: a thin strip at the very RIGHT EDGE (in the page margin) that
            // detects vertical drags. It CONSUMES the drag, so the navigator never page-turns while
            // adjusting; being only a narrow edge strip, normal taps / reading / page swipes never
            // hit it. detectVerticalDragGestures claims only vertical drags, so a horizontal swipe
            // that grazes the edge still pages.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(BRIGHTNESS_EDGE_WIDTH)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    brightnessHideJob?.cancel()
                                    val base = brightnessIndicator ?: (brightness ?: 0.5f)
                                    val next = nextBrightness(base, dragAmount, heightPx)
                                    brightnessIndicator = next
                                    viewModel.setBrightness(next)
                                },
                                onDragEnd = {
                                    brightnessHideJob = brightnessScope.launch {
                                        delay(600)
                                        brightnessIndicator = null
                                    }
                                },
                            )
                        },
                )
                brightnessIndicator?.let { level ->
                    BrightnessIndicator(
                        level = level,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp),
                    )
                }
            }
        }
    }

    // Rendered last so it draws over the reader; the full-screen opaque Surface covers the navigator.
    if (searchVisible) {
        ReaderSearchScreen(
            state = searchState,
            onQuery = viewModel::search,
            onResultClick = { result ->
                viewModel.goTo(result.locator)
                viewModel.clearSearch()
                searchVisible = false
            },
            onClose = {
                viewModel.clearSearch()
                searchVisible = false
            },
        )
    }
}

/** Walks the [ContextWrapper] chain to find the host [Activity], if any. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
@Composable
private fun EpubReader(
    bookId: Long,
    state: ReaderUiState.Ready,
    epubPreferences: kotlinx.coroutines.flow.StateFlow<org.readium.r2.navigator.epub.EpubPreferences>,
    highlight: kotlinx.coroutines.flow.StateFlow<com.reader.feature.reader.highlight.HighlightState>,
    navigateRequests: kotlinx.coroutines.flow.SharedFlow<org.readium.r2.shared.publication.Locator>,
    onLocatorChanged: (Long, org.readium.r2.shared.publication.Locator) -> Unit,
    onSelection: (SelectionEvent) -> Unit,
    onSaveWord: (term: String, translation: String, contextSentence: String?) -> Unit,
) {
    val translationVm: TranslationViewModel = hiltViewModel()
    val popup by translationVm.popupState.collectAsStateWithLifecycle()

    // Dictionary lookup for single-word taps, rendered in a modal bottom sheet.
    val wordVm: WordLookupViewModel = hiltViewModel()
    val wordState by wordVm.lookupState.collectAsStateWithLifecycle()
    val canSpeak by wordVm.ttsAvailable.collectAsStateWithLifecycle()

    // Anchor for the popover: the tapped word's bounds in navigator-view pixels.
    var anchorRect by remember { mutableStateOf<RectF?>(null) }

    // The enclosing sentence of the last selection (null for long-press), stashed so a Save
    // tap on the resulting translation can persist it as context.
    var lastContextSentence by remember { mutableStateOf<String?>(null) }

    // Forward selection events to the host while also driving translation + anchoring.
    val currentOnSelection by rememberUpdatedState(onSelection)
    val onSelectionInternal: (SelectionEvent) -> Unit = remember {
        { event ->
            if (event.isWord) {
                // Single-word tap → dictionary bottom sheet. Stash the context for Save; do NOT
                // touch the popover's anchor or trigger its translation.
                lastContextSentence = event.contextSentence
                wordVm.onWord(event.text)
            } else {
                // Long-press phrase/sentence → existing translation popover (unchanged).
                anchorRect = event.rectInView
                lastContextSentence = event.contextSentence
                translationVm.onTextSelected(event.text)
            }
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
                highlight = highlight,
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

    // Route navigate requests (chapter jump / fraction seek) from the ViewModel through the
    // session's goTo hook, which the fragment fulfils against the live navigator.
    LaunchedEffect(sessionId, navigateRequests) {
        navigateRequests.collect { locator ->
            ReaderNavigatorHost.get(sessionId)?.goTo?.invoke(locator)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AndroidFragment<EpubReaderFragment>(
            modifier = Modifier.fillMaxSize(),
            arguments = EpubReaderFragment.argsFor(sessionId),
        )

        val currentPopup = popup
        val rect = anchorRect
        if (currentPopup != null && rect != null) {
            val gapPx = with(LocalDensity.current) { GAP_DP.dp.toPx() }
            val maxW = constraints.maxWidth
            val maxH = constraints.maxHeight

            var cardSize by remember { mutableStateOf(IntSize.Zero) }
            // Full-screen scrim (PARENT) catches taps outside the card and dismisses it, consuming
            // the tap so it doesn't pass through to translate the word under it. The card is its
            // CHILD, so taps on the card (and its Save button) are hit-tested first and never reach
            // the scrim. Tapping again — card gone — reaches the navigator and translates.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentPopup) {
                        detectTapGestures {
                            anchorRect = null
                            translationVm.dismiss()
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .onSizeChanged { cardSize = it }
                        .offset {
                            val centerX = ((rect.left + rect.right) / 2f).toInt()
                            val x = (centerX - cardSize.width / 2)
                                .coerceIn(0, (maxW - cardSize.width).coerceAtLeast(0))
                            val below = (rect.bottom + gapPx).toInt()
                            val above = (rect.top - gapPx).toInt() - cardSize.height
                            val y = (if (below + cardSize.height <= maxH) below else above)
                                .coerceIn(0, (maxH - cardSize.height).coerceAtLeast(0))
                            IntOffset(x, y)
                        }
                        // Consume taps on the card area so they don't reach the scrim and dismiss;
                        // the Save button (deeper child) still gets its taps first.
                        .pointerInput(currentPopup) { detectTapGestures { } },
                ) {
                    TranslationPopover(
                        state = currentPopup,
                        onDismiss = {
                            anchorRect = null
                            translationVm.dismiss()
                        },
                        onSave = {
                            val result = currentPopup as? com.reader.feature.translation.TranslationPopupState.Result
                            if (result != null) {
                                onSaveWord(result.source, result.translation, lastContextSentence)
                                anchorRect = null
                                translationVm.dismiss()
                            }
                        },
                    )
                }
            }
        }

        // Single-word tap → dictionary bottom sheet (ModalBottomSheet dismisses natively, so it
        // needs no custom scrim/catcher like the phrase popover above).
        wordState?.let { currentWordState ->
            WordDictionarySheet(
                state = currentWordState,
                onSave = { term, translation ->
                    onSaveWord(term, translation, lastContextSentence)
                    wordVm.dismiss()
                },
                onDismiss = { wordVm.dismiss() },
                canSpeak = canSpeak,
                onSpeak = wordVm::speak,
            )
        }
    }
}

private const val GAP_DP = 8
