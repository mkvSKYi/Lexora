package com.reader.feature.translation

sealed interface WordLookupState {
    data object Loading : WordLookupState

    data class Entry(
        val word: String,
        val ipa: String?,
        val partOfSpeech: String?,
        val definitions: List<String>,
        val translations: List<String>,
        /** ML Kit machine translation shown alongside the dictionary article; null if unavailable. */
        val machineTranslation: String? = null,
        /** True while the machine translation is still being fetched. */
        val translationPending: Boolean = false,
    ) : WordLookupState

    data class Machine(
        val word: String,
        val translation: String,
    ) : WordLookupState

    data class Error(val message: String) : WordLookupState
}
