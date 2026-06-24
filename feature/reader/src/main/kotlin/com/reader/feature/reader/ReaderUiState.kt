package com.reader.feature.reader

import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** UI state for the reader screen. */
sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Ready(
        val publication: Publication,
        val initialLocator: Locator?,
    ) : ReaderUiState

    data class Error(val message: String) : ReaderUiState
}
