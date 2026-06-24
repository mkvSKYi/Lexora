package com.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.LibraryRepository
import com.reader.core.data.model.ReadingProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val publicationOpener: PublicationOpener,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    fun load(bookId: Long) {
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
