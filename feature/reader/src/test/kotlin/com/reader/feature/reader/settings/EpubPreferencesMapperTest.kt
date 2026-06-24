package com.reader.feature.reader.settings

import android.graphics.Color as AndroidColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.robolectric.RobolectricTestRunner

// Readium's Theme enum touches android.graphics.Color in its static init → Robolectric.
@OptIn(ExperimentalReadiumApi::class)
@RunWith(RobolectricTestRunner::class)
class EpubPreferencesMapperTest {
    @Test fun light_preset_maps_to_light_theme() {
        val out = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.LIGHT)
        assertEquals(Theme.LIGHT, out.theme)
    }

    @Test fun dark_preset_maps_to_dark_theme() {
        val out = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.DARK)
        assertEquals(Theme.DARK, out.theme)
    }

    @Test fun preset_roundtrips_for_sepia() {
        val out = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.SEPIA)
        assertEquals(ReaderThemePreset.SEPIA, EpubPreferencesMapper.presetOf(out))
    }

    @Test fun amoled_preset_maps_to_dark_theme_with_black_background() {
        val out = EpubPreferencesMapper.withTheme(EpubPreferences(), ReaderThemePreset.AMOLED)
        assertEquals(Theme.DARK, out.theme)
        assertEquals(Color(AndroidColor.BLACK), out.backgroundColor)
        assertEquals(Color(AndroidColor.WHITE), out.textColor)
        assertEquals(ReaderThemePreset.AMOLED, EpubPreferencesMapper.presetOf(out))
    }

    @Test fun unset_theme_returns_null_preset() {
        assertNull(EpubPreferencesMapper.presetOf(EpubPreferences()))
    }
}
