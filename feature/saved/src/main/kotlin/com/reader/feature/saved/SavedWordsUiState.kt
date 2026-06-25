package com.reader.feature.saved

import com.reader.core.data.model.SavedWord

sealed interface SavedWordsUiState {
    data object Loading : SavedWordsUiState
    data class Content(
        val words: List<SavedWord>,
        val learnedCount: Int,
        val totalCount: Int,
        val dueCount: Int,
    ) : SavedWordsUiState
}
