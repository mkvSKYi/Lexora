package com.reader.core.data.model

data class Bookmark(
    val id: Long,
    val bookId: Long,
    val locatorJson: String,
    val href: String,
    val progression: Double,
    val totalProgression: Double,
    val chapterTitle: String?,
    val createdAt: Long,
)
