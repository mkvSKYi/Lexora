package com.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.LibraryRepository
import com.reader.core.data.model.ReadingProgress
import com.reader.core.data.preferences.ReaderPreferencesRepository
import com.reader.feature.reader.navigation.TocEntry
import com.reader.feature.reader.navigation.TocResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import javax.inject.Inject

@OptIn(ExperimentalReadiumApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val publicationOpener: PublicationOpener,
    private val preferencesRepository: ReaderPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val preferencesSerializer = EpubPreferencesSerializer()

    private val _epubPreferences = MutableStateFlow(EpubPreferences())

    /**
     * The current Readium reading-appearance preferences, loaded from persistence and updated
     * via [updateEpubPreferences]. Always emits a valid value; a malformed persisted JSON
     * falls back to defaults rather than crashing.
     */
    val epubPreferences: StateFlow<EpubPreferences> = _epubPreferences.asStateFlow()

    private val _brightness = MutableStateFlow<Float?>(null)

    /** Screen brightness override in 0f..1f, or null to follow the system. */
    val brightness: StateFlow<Float?> = _brightness.asStateFlow()

    private val _warmth = MutableStateFlow(0f)

    /** Warmth (amber overlay) intensity in 0f..1f; 0 = off. */
    val warmth: StateFlow<Float> = _warmth.asStateFlow()

    private val _toc = MutableStateFlow<List<TocEntry>>(emptyList())

    /**
     * The flattened table of contents for the open publication, each entry's
     * [TocEntry.locator] resolved against the publication. Empty until a publication is Ready and
     * its TOC has loaded.
     */
    val toc: StateFlow<List<TocEntry>> = _toc.asStateFlow()

    /** Spine hrefs (fragment-stripped), in reading order, used for current-chapter detection. */
    private var readingOrderHrefs: List<String> = emptyList()

    /** All publication positions, loaded on Ready; used to resolve a fraction seek to a locator. */
    private var positions: List<Locator> = emptyList()

    private val _currentLocator = MutableStateFlow<Locator?>(null)

    private val _currentProgression = MutableStateFlow(0f)

    /** Reading progress of the current position in 0f..1f (from `totalProgression`; 0 default). */
    val currentProgression: StateFlow<Float> = _currentProgression.asStateFlow()

    private val _currentChapterTitle = MutableStateFlow<String?>(null)

    /** Title of the TOC entry for the current position, or null when none resolves. */
    val currentChapterTitle: StateFlow<String?> = _currentChapterTitle.asStateFlow()

    private val _currentChapterHref = MutableStateFlow<String?>(null)

    /** Href of the TOC entry for the current position, used to highlight the active chapter. */
    val currentChapterHref: StateFlow<String?> = _currentChapterHref.asStateFlow()

    private val _navigateRequests = MutableSharedFlow<Locator>(extraBufferCapacity = 1)

    /**
     * Locators the navigator host should jump to. Emitted by [goTo]/[seekTo] and collected by the
     * reader screen, which routes each one through the [ReaderNavigatorHost.Session] go hook.
     */
    val navigateRequests: SharedFlow<Locator> = _navigateRequests.asSharedFlow()

    init {
        // Seed once from persistence. The ViewModel is the only writer of these preferences
        // during a reading session, so continuously re-collecting observe() would echo our own
        // persisted writes back into the StateFlows and race rapid changes. A fresh ViewModel on
        // the next book open seeds again, preserving global persistence across books.
        viewModelScope.launch {
            val prefs = preferencesRepository.observe().first()
            _epubPreferences.value = deserialize(prefs.epubPreferencesJson)
            _brightness.value = prefs.brightness
            _warmth.value = prefs.warmth
        }
    }

    /** Updates the live brightness override and persists it. Null follows the system. */
    fun setBrightness(value: Float?) {
        _brightness.value = value
        viewModelScope.launch { preferencesRepository.setBrightness(value) }
    }

    /** Updates the live warmth intensity and persists it. */
    fun setWarmth(value: Float) {
        _warmth.value = value
        viewModelScope.launch { preferencesRepository.setWarmth(value) }
    }

    /** Updates the live preferences and persists them as Readium-serialized JSON. */
    fun updateEpubPreferences(prefs: EpubPreferences) {
        _epubPreferences.value = prefs
        viewModelScope.launch {
            preferencesRepository.setEpubPreferencesJson(preferencesSerializer.serialize(prefs))
        }
    }

    private fun deserialize(json: String?): EpubPreferences {
        if (json == null) return EpubPreferences()
        return runCatching { preferencesSerializer.deserialize(json) }
            .getOrDefault(EpubPreferences())
    }

    private var saveJob: Job? = null

    fun load(bookId: Long) {
        val previousPublication = (_uiState.value as? ReaderUiState.Ready)?.publication
        _uiState.value = ReaderUiState.Loading
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                _uiState.value = ReaderUiState.Error("Book not found")
                return@launch
            }
            val publication = publicationOpener.open(book.filePath)
            if (publication == null) {
                _uiState.value = ReaderUiState.Error("Unable to open this book")
                return@launch
            }
            previousPublication?.close()
            val initialLocator = repository.getProgress(bookId)
                ?.locatorJson
                ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
            repository.markOpened(bookId, System.currentTimeMillis())
            loadNavigationData(publication)
            _uiState.value = ReaderUiState.Ready(publication, initialLocator)
        }
    }

    /**
     * Loads the TOC (each entry's locator resolved via [Publication.locatorFromLink]), the reading
     * order, and all positions for the open [publication]. Resets derived current-chapter/progress
     * state for the new book.
     */
    private suspend fun loadNavigationData(publication: Publication) {
        readingOrderHrefs = publication.readingOrder.map { it.href.toString().substringBefore('#') }
        positions = runCatching { publication.positions() }.getOrDefault(emptyList())

        val tocLinks = publication.tableOfContents
        val flattened = TocResolver.flatten(tocLinks)
        // Re-walk the same links in the same depth-first order flatten uses, so each flattened
        // entry lines up with its source link and we can resolve the entry's locator from it.
        val orderedLinks = flattenLinks(tocLinks)
        _toc.value = flattened.mapIndexed { index, entry ->
            val link = orderedLinks.getOrNull(index)
            val locator = link?.let { publication.locatorFromLink(it) }
            entry.copy(locator = locator)
        }

        _currentLocator.value = null
        _currentProgression.value = 0f
        _currentChapterTitle.value = null
        _currentChapterHref.value = null
    }

    /** Depth-first flatten of links, matching [TocResolver.flatten]'s traversal order. */
    private fun flattenLinks(
        links: List<org.readium.r2.shared.publication.Link>,
    ): List<org.readium.r2.shared.publication.Link> =
        links.flatMap { listOf(it) + flattenLinks(it.children) }

    /**
     * Requests a jump to [locator] via the navigator host. Also optimistically updates the derived
     * current-chapter/progression state; the navigator will subsequently confirm via
     * [onLocatorChanged].
     */
    fun goTo(locator: Locator) {
        updateDerivedState(locator)
        _navigateRequests.tryEmit(locator)
    }

    /**
     * Seeks to the position closest to [fraction] (0f..1f) over the loaded positions and jumps
     * there. No-op when positions haven't loaded.
     */
    fun seekTo(fraction: Float) {
        val locator = TocResolver.positionForFraction(positions, fraction) ?: return
        goTo(locator)
    }

    /**
     * Persists the current reading position. Saves are debounced so rapid locator
     * updates (e.g. while paginating) collapse into a single write.
     */
    fun onLocatorChanged(bookId: Long, locator: Locator) {
        updateDerivedState(locator)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            repository.saveProgress(
                ReadingProgress(
                    bookId = bookId,
                    locatorJson = locator.toJSON().toString(),
                    percent = locator.locations.totalProgression ?: 0.0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Updates the current locator and re-derives [currentProgression] and [currentChapterTitle]
     * from it. Current chapter uses the reading-order-aware [TocResolver.currentEntryIndex] so a
     * resource without its own TOC entry maps to the closest preceding chapter in spine order.
     */
    private fun updateDerivedState(locator: Locator) {
        _currentLocator.value = locator
        _currentProgression.value = (locator.locations.totalProgression ?: 0.0).toFloat()

        val currentHref = locator.href.toString().substringBefore('#')
        val entries = _toc.value
        val index = TocResolver.currentEntryIndex(entries, readingOrderHrefs, currentHref)
        val entry = index?.let { entries.getOrNull(it) }
        _currentChapterTitle.value = entry?.title
        _currentChapterHref.value = entry?.href
    }

    override fun onCleared() {
        super.onCleared()
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
