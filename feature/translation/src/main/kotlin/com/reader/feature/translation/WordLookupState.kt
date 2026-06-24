package com.reader.feature.translation

sealed interface WordLookupState {
    data object Loading : WordLookupState

    data class Entry(
        val word: String,
        val ipa: String?,
        val partOfSpeech: String?,
        val definitions: List<String>,
        val translations: List<String>,
    ) : WordLookupState

    data class Machine(
        val word: String,
        val translation: String,
    ) : WordLookupState

    data class Error(val message: String) : WordLookupState
}
