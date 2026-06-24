package com.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Long,
    val locatorJson: String?,
    val percent: Double,
    val updatedAt: Long,
)
