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
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueAt: Long = 0,
    val lastReviewedAt: Long? = null,
)
