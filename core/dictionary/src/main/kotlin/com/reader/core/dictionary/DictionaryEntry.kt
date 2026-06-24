package com.reader.core.dictionary

data class DictionaryEntry(
    val headword: String,
    val ipa: String?,
    val partOfSpeech: String?,
    val definitions: List<String>,
    val translations: List<String>,
)
