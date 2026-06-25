package com.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per calendar day the user was active, keyed by [epochDay] (`LocalDate.toEpochDay()`).
 * Backs the dashboard streak + heatmap. A day is "active" if any of the flags/counts are set.
 */
@Entity(tableName = "activity_days")
data class DailyActivityEntity(
    @PrimaryKey val epochDay: Long,
    val readingActive: Boolean = false,
    val wordsSaved: Int = 0,
    val wordsReviewed: Int = 0,
)
