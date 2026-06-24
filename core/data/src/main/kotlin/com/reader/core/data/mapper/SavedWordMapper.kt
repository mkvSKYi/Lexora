package com.reader.core.data.mapper

import com.reader.core.data.model.SavedWord
import com.reader.core.database.entity.SavedWordEntity

fun SavedWordEntity.toDomain(): SavedWord = SavedWord(
    id = id,
    term = term,
    translation = translation,
    contextSentence = contextSentence,
    bookId = bookId,
    bookTitle = bookTitle,
    createdAt = createdAt,
)

fun SavedWord.toEntity(): SavedWordEntity = SavedWordEntity(
    id = id,
    term = term,
    translation = translation,
    contextSentence = contextSentence,
    bookId = bookId,
    bookTitle = bookTitle,
    createdAt = createdAt,
)
