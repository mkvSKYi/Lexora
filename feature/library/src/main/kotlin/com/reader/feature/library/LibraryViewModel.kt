package com.reader.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.LibraryRepository
import com.reader.core.data.imports.EpubImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val importer: EpubImporter,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        repo.observeBooks()
            .map<List<com.reader.core.data.model.Book>, LibraryUiState> { LibraryUiState.Content(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LibraryUiState.Loading,
            )

    private val _importErrors = Channel<String>(Channel.BUFFERED)
    val importErrors: Flow<String> = _importErrors.receiveAsFlow()

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            // EpubImporter is not main-safe (ContentResolver IO + Readium parsing).
            val result = withContext(Dispatchers.IO) { importer.import(uri) }
            result.onFailure { error ->
                _importErrors.send(error.message ?: "Failed to import book")
            }
        }
    }

    fun deleteBook(book: com.reader.core.data.model.Book) {
        viewModelScope.launch { repo.deleteBookCompletely(book) }
    }

    suspend fun progressPercent(bookId: Long): Double = repo.progressPercent(bookId)
}
