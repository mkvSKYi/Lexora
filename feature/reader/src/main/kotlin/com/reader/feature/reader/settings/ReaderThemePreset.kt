package com.reader.feature.reader.settings

/**
 * High-level reading appearance presets exposed to the user.
 *
 * Each preset maps to a concrete Readium [org.readium.r2.navigator.epub.EpubPreferences]
 * via [EpubPreferencesMapper]. [AMOLED] is a true-black variant of [DARK].
 */
enum class ReaderThemePreset {
    LIGHT,
    SEPIA,
    DARK,
    AMOLED,
    PAPER,
    NORD,
    SOLARIZED_DARK,
    GRUVBOX,
    DUSK,
}
