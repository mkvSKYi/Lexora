package com.reader.feature.saved.review

import com.reader.core.data.model.SavedWord

sealed interface ReviewSessionUiState {
    data object Loading : ReviewSessionUiState
    data object Empty : ReviewSessionUiState
    data class Reviewing(
        val card: SavedWord,
        val revealed: Boolean,
        val position: Int,   // 1-based, among distinct cards passed
        val total: Int,
        val againLabel: String,
        val goodLabel: String,
        val easyLabel: String,
    ) : ReviewSessionUiState
    data class Summary(val reviewed: Int, val learned: Int) : ReviewSessionUiState
}
