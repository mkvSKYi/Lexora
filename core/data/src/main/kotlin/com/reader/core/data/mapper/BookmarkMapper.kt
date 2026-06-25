package com.reader.core.data.mapper

import com.reader.core.data.model.Bookmark
import com.reader.core.database.entity.BookmarkEntity

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id, bookId, locatorJson, href, progression, totalProgression, chapterTitle, createdAt,
)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id, bookId, locatorJson, href, progression, totalProgression, chapterTitle, createdAt,
)
