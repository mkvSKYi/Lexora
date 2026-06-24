package com.reader.feature.translation

sealed interface TranslationPopupState {
    data object Loading : TranslationPopupState
    data class Result(val source: String, val translation: String) : TranslationPopupState
    data class Error(val message: String) : TranslationPopupState
}
