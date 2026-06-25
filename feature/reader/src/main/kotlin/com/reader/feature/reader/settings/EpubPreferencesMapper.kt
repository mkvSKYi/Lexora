package com.reader.feature.reader.settings

import android.graphics.Color as AndroidColor
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Maps [ReaderThemePreset] presets to Readium [EpubPreferences] and back.
 *
 * Readium has no native AMOLED theme, so it is modelled as [Theme.DARK] plus an
 * explicit true-black [EpubPreferences.backgroundColor] (and white text).
 */
@OptIn(ExperimentalReadiumApi::class)
object EpubPreferencesMapper {

    private val BLACK: Color = Color(AndroidColor.BLACK)
    private val WHITE: Color = Color(AndroidColor.WHITE)

    // Pure hex → opaque ARGB Color (no android.graphics.Color.parseColor, so this is JVM-testable).
    private fun color(hex: String): Color = Color((0xFF000000L or hex.removePrefix("#").toLong(16)).toInt())

    // Custom palettes: each has a UNIQUE background so presetOf can reverse-map by it.
    private val PAPER_BG = color("#F5EFE0")
    private val PAPER_FG = color("#2B2620")
    private val NORD_BG = color("#2E3440")
    private val NORD_FG = color("#ECEFF4")
    private val SOLARIZED_BG = color("#002B36")
    private val SOLARIZED_FG = color("#93A1A1")
    private val GRUVBOX_BG = color("#282828")
    private val GRUVBOX_FG = color("#EBDBB2")
    private val DUSK_BG = color("#20232E")
    private val DUSK_FG = color("#C8CCDA")

    /**
     * Returns [base] with the theme-related preferences set for [preset].
     *
     * For [ReaderThemePreset.AMOLED] the background/text colors are forced to
     * black/white; for the other presets they are cleared so Readium uses the
     * theme's own palette.
     */
    fun withTheme(base: EpubPreferences, preset: ReaderThemePreset): EpubPreferences =
        when (preset) {
            ReaderThemePreset.LIGHT ->
                base.copy(theme = Theme.LIGHT, backgroundColor = null, textColor = null)
            ReaderThemePreset.SEPIA ->
                base.copy(theme = Theme.SEPIA, backgroundColor = null, textColor = null)
            ReaderThemePreset.DARK ->
                base.copy(theme = Theme.DARK, backgroundColor = null, textColor = null)
            ReaderThemePreset.AMOLED ->
                base.copy(theme = Theme.DARK, backgroundColor = BLACK, textColor = WHITE)
            ReaderThemePreset.PAPER ->
                base.copy(theme = Theme.LIGHT, backgroundColor = PAPER_BG, textColor = PAPER_FG)
            ReaderThemePreset.NORD ->
                base.copy(theme = Theme.DARK, backgroundColor = NORD_BG, textColor = NORD_FG)
            ReaderThemePreset.SOLARIZED_DARK ->
                base.copy(theme = Theme.DARK, backgroundColor = SOLARIZED_BG, textColor = SOLARIZED_FG)
            ReaderThemePreset.GRUVBOX ->
                base.copy(theme = Theme.DARK, backgroundColor = GRUVBOX_BG, textColor = GRUVBOX_FG)
            ReaderThemePreset.DUSK ->
                base.copy(theme = Theme.DARK, backgroundColor = DUSK_BG, textColor = DUSK_FG)
        }

    /**
     * Returns the preset matching [prefs], or `null` if no theme is set.
     *
     * A [Theme.DARK] preference with a true-black background is reported as
     * [ReaderThemePreset.AMOLED]; otherwise the result follows [EpubPreferences.theme].
     */
    fun presetOf(prefs: EpubPreferences): ReaderThemePreset? =
        when (prefs.backgroundColor) {
            PAPER_BG -> ReaderThemePreset.PAPER
            NORD_BG -> ReaderThemePreset.NORD
            SOLARIZED_BG -> ReaderThemePreset.SOLARIZED_DARK
            GRUVBOX_BG -> ReaderThemePreset.GRUVBOX
            DUSK_BG -> ReaderThemePreset.DUSK
            BLACK -> if (prefs.theme == Theme.DARK) ReaderThemePreset.AMOLED else null
            else -> when (prefs.theme) {
                Theme.LIGHT -> ReaderThemePreset.LIGHT
                Theme.SEPIA -> ReaderThemePreset.SEPIA
                Theme.DARK -> ReaderThemePreset.DARK
                null -> null
            }
        }
}
