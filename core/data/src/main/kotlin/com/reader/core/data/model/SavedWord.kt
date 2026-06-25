package com.reader.core.data.model

data class SavedWord(
    val id: Long,
    val term: String,
    val translation: String,
    val contextSentence: String?,
    val bookId: Long,
    val bookTitle: String,
    val createdAt: Long,
    val learned: Boolean,
)
