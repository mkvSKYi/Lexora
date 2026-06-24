package com.reader.core.data.model

data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)
