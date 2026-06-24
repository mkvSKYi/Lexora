package com.reader.core.data.model

/** A book paired with its reading progress as a 0..1 fraction. */
data class BookWithProgress(
    val book: Book,
    val percent: Double,
)
