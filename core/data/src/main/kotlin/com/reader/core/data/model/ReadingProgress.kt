package com.reader.core.data.model

data class ReadingProgress(
    val bookId: Long,
    val locatorJson: String?,
    val percent: Double,
    val updatedAt: Long,
)
