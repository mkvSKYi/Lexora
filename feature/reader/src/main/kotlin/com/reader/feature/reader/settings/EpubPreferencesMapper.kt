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
        }

    /**
     * Returns the preset matching [prefs], or `null` if no theme is set.
     *
     * A [Theme.DARK] preference with a true-black background is reported as
     * [ReaderThemePreset.AMOLED]; otherwise the result follows [EpubPreferences.theme].
     */
    fun presetOf(prefs: EpubPreferences): ReaderThemePreset? =
        when (prefs.theme) {
            Theme.LIGHT -> ReaderThemePreset.LIGHT
            Theme.SEPIA -> ReaderThemePreset.SEPIA
            Theme.DARK ->
                if (prefs.backgroundColor == BLACK) ReaderThemePreset.AMOLED
                else ReaderThemePreset.DARK
            null -> null
        }
}
