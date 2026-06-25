package com.reader.core.data.preferences

/**
 * Global reading-appearance preferences (not per-book).
 *
 * @param epubPreferencesJson the Readium-serialized [org.readium.r2.navigator.epub.EpubPreferences]
 *   as JSON, or null when none have been set yet.
 * @param brightness screen brightness override in 0f..1f, or null to follow the system.
 * @param warmth screen warmth in 0f..1f (0 = off).
 * @param highlightSavedWords whether saved words are highlighted in the reading view.
 */
data class ReaderPreferences(
    val epubPreferencesJson: String?,
    val brightness: Float?,
    val warmth: Float,
    val highlightSavedWords: Boolean = true,
    val lockRotation: Boolean = false,
)
