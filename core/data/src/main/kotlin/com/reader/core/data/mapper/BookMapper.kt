package com.reader.core.data.mapper

import com.reader.core.data.model.Book
import com.reader.core.data.model.BookWithProgress
import com.reader.core.data.model.ReadingProgress
import com.reader.core.database.dao.BookWithProgressRow
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.ReadingProgressEntity

fun BookWithProgressRow.toDomain(): BookWithProgress = BookWithProgress(book.toDomain(), percent)

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    coverPath = coverPath,
    filePath = filePath,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    coverPath = coverPath,
    filePath = filePath,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
)

fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
    bookId = bookId,
    locatorJson = locatorJson,
    percent = percent,
    updatedAt = updatedAt,
)

fun ReadingProgress.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
    bookId = bookId,
    locatorJson = locatorJson,
    percent = percent,
    updatedAt = updatedAt,
)
