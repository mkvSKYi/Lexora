package com.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId"])])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locatorJson: String,
    val href: String,
    val progression: Double,
    val totalProgression: Double,
    val chapterTitle: String?,
    val createdAt: Long,
)
