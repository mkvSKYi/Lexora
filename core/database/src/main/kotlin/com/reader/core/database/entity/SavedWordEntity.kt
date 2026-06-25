package com.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_words",
    indices = [Index(value = ["term", "bookId"], unique = true)],
)
data class SavedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val translation: String,
    val contextSentence: String?,
    val bookId: Long,
    val bookTitle: String,
    val createdAt: Long,
    val learned: Boolean = false,
)
