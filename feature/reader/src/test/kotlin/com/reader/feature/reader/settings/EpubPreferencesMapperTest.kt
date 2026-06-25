package com.reader.feature.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.Theme
import org.robolectric.RobolectricTestRunner

// Robolectric: Readium's Theme enum initializes default colors via android.graphics.Color, which a
// plain JVM test can't run.
@RunWith(RobolectricTestRunner::class)
class EpubPreferencesMapperTest {

    @Test fun paper_sets_light_theme_and_warm_colors() {
        val p = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.PAPER)
        assertEquals(Theme.LIGHT, p.theme)
        assertEquals(Color(0xFFF5EFE0.toInt()), p.backgroundColor)
        assertEquals(Color(0xFF2B2620.toInt()), p.textColor)
    }

    @Test fun nord_sets_dark_theme_and_colors() {
        val p = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.NORD)
        assertEquals(Theme.DARK, p.theme)
        assertEquals(Color(0xFF2E3440.toInt()), p.backgroundColor)
        assertEquals(Color(0xFFECEFF4.toInt()), p.textColor)
    }

    @Test fun every_preset_round_trips() {
        for (preset in ReaderThemePreset.entries) {
            val prefs = EpubPreferencesMapper.withTheme(EpubPreferences(), preset)
            assertEquals(preset, EpubPreferencesMapper.presetOf(prefs))
        }
    }
}
