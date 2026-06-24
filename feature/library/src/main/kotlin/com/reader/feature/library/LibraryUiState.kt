package com.reader.feature.library

import com.reader.core.data.model.Book

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Content(val books: List<Book>) : LibraryUiState
}
