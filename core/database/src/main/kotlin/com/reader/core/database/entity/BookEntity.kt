package com.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)
