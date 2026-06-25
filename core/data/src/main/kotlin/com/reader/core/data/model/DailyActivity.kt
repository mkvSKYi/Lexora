package com.reader.core.data.model

/** A single day's reading-app activity, backing the dashboard streak + heatmap. */
data class DailyActivity(
    val epochDay: Long,
    val readingActive: Boolean,
    val wordsSaved: Int,
    val wordsReviewed: Int,
) {
    /** Combined intensity for the heatmap shade (reading counts as one event). */
    val total: Int get() = (if (readingActive) 1 else 0) + wordsSaved + wordsReviewed

    val isActive: Boolean get() = readingActive || wordsSaved > 0 || wordsReviewed > 0
}
