package com.reader.core.database.dao

import androidx.room.Embedded
import com.reader.core.database.entity.BookEntity

/** A book joined with its reading progress percent (0.0 when no progress row exists). */
data class BookWithProgressRow(
    @Embedded val book: BookEntity,
    val percent: Double,
)
