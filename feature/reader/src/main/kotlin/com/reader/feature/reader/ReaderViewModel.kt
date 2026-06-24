package com.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.LibraryRepository
import com.reader.core.data.model.ReadingProgress
import com.reader.core.data.preferences.ReaderPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
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

    init {
        viewModelScope.launch {
            preferencesRepository.observe().collect { prefs ->
                _epubPreferences.value = deserialize(prefs.epubPreferencesJson)
                _brightness.value = prefs.brightness
                _warmth.value = prefs.warmth
            }
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
            _uiState.value = ReaderUiState.Ready(publication, initialLocator)
        }
    }

    /**
     * Persists the current reading position. Saves are debounced so rapid locator
     * updates (e.g. while paginating) collapse into a single write.
     */
    fun onLocatorChanged(bookId: Long, locator: Locator) {
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

    override fun onCleared() {
        super.onCleared()
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
