package com.reader.feature.library

import com.reader.core.data.model.BookWithProgress

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Content(val books: List<BookWithProgress>) : LibraryUiState
}
