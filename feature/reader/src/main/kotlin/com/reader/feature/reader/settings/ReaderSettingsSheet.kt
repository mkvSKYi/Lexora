package com.reader.feature.reader.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.unit.dp
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.math.roundToInt

/** Font-size multiplier bounds (1.0 == 100% of the publisher default). */
private const val FONT_SIZE_MIN = 0.5
private const val FONT_SIZE_MAX = 2.5
private const val FONT_SIZE_STEP = 0.1
private const val FONT_SIZE_DEFAULT = 1.0

/** Line-height multiplier bounds. Readium honors this only when publisher styles are off. */
private const val LINE_HEIGHT_MIN = 1.0f
private const val LINE_HEIGHT_MAX = 2.0f
private const val LINE_HEIGHT_DEFAULT = 1.5f
private const val LINE_HEIGHT_STEPS = 9 // 0.1 increments across the range

/** Page-margins multiplier bounds (1.0 == default). */
private const val PAGE_MARGINS_MIN = 0.5f
private const val PAGE_MARGINS_MAX = 2.0f
private const val PAGE_MARGINS_DEFAULT = 1.0f
private const val PAGE_MARGINS_STEPS = 5 // 0.25 increments across the range

/** Slider position used when no brightness override is set yet (mid-scale). */
private const val BRIGHTNESS_DEFAULT = 0.5f

private val SWATCH_SIZE = 44.dp

/** A theme preset paired with the swatch colors that preview it. */
private data class ThemeSwatch(
    val preset: ReaderThemePreset,
    val label: String,
    val background: ComposeColor,
    val foreground: ComposeColor,
)

private val THEME_SWATCHES = listOf(
    ThemeSwatch(ReaderThemePreset.LIGHT, "Light", ComposeColor(0xFFFFFFFF), ComposeColor(0xFF121212)),
    ThemeSwatch(ReaderThemePreset.SEPIA, "Sepia", ComposeColor(0xFFFAF4E8), ComposeColor(0xFF5B4636)),
    ThemeSwatch(ReaderThemePreset.DARK, "Dark", ComposeColor(0xFF1E1E1E), ComposeColor(0xFFE0E0E0)),
    ThemeSwatch(ReaderThemePreset.AMOLED, "AMOLED", ComposeColor(0xFF000000), ComposeColor(0xFFFFFFFF)),
)

/**
 * Reading-appearance bottom sheet: theme presets, typography, and page mode.
 *
 * Each control derives a new [EpubPreferences] from [prefs] (via [EpubPreferencesMapper] or
 * `copy`) and calls [onPrefsChange]; the caller persists the result and the navigator
 * re-applies it live. Advanced typography (line height) requires publisher styles off, so
 * adjusting the line height also disables them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderSettingsSheet(
    prefs: EpubPreferences,
    onPrefsChange: (EpubPreferences) -> Unit,
    brightness: Float?,
    warmth: Float,
    onBrightnessChange: (Float?) -> Unit,
    onWarmthChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ThemeRow(prefs = prefs, onPrefsChange = onPrefsChange)
            FontSizeRow(prefs = prefs, onPrefsChange = onPrefsChange)
            FontFamilyRow(prefs = prefs, onPrefsChange = onPrefsChange)
            LineHeightRow(prefs = prefs, onPrefsChange = onPrefsChange)
            PageMarginsRow(prefs = prefs, onPrefsChange = onPrefsChange)
            PageModeRow(prefs = prefs, onPrefsChange = onPrefsChange)
            BrightnessRow(brightness = brightness, onBrightnessChange = onBrightnessChange)
            WarmthRow(warmth = warmth, onWarmthChange = onWarmthChange)
        }
    }
}

/** Brightness override slider (0..1) with a "System" reset that follows device brightness. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessRow(brightness: Float?, onBrightnessChange: (Float?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SectionLabel("Brightness")
        TextButton(
            onClick = { onBrightnessChange(null) },
            enabled = brightness != null,
        ) {
            Text("System")
        }
    }
    Slider(
        value = brightness ?: BRIGHTNESS_DEFAULT,
        onValueChange = { onBrightnessChange(it) },
        valueRange = 0f..1f,
    )
}

/** Warmth (amber overlay) slider (0..1); 0 = off. */
@Composable
private fun WarmthRow(warmth: Float, onWarmthChange: (Float) -> Unit) {
    SectionLabel("Warmth")
    Slider(
        value = warmth.coerceIn(0f, 1f),
        onValueChange = { onWarmthChange(it) },
        valueRange = 0f..1f,
    )
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ThemeRow(prefs: EpubPreferences, onPrefsChange: (EpubPreferences) -> Unit) {
    val active = EpubPreferencesMapper.presetOf(prefs)
    SectionLabel("Theme")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        THEME_SWATCHES.forEach { swatch ->
            ThemeSwatchItem(
                swatch = swatch,
                selected = swatch.preset == active,
                onClick = { onPrefsChange(EpubPreferencesMapper.withTheme(prefs, swatch.preset)) },
            )
        }
    }
}

@Composable
private fun ThemeSwatchItem(swatch: ThemeSwatch, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val borderColor =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        val borderWidth = if (selected) 3.dp else 1.dp
        Column(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .border(borderWidth, borderColor, CircleShape)
                .background(swatch.background, CircleShape)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Aa",
                color = swatch.foreground,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = swatch.label,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun FontSizeRow(prefs: EpubPreferences, onPrefsChange: (EpubPreferences) -> Unit) {
    val current = prefs.fontSize ?: FONT_SIZE_DEFAULT
    SectionLabel("Font size")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedIconButton(
            onClick = {
                val next = (current - FONT_SIZE_STEP).coerceAtLeast(FONT_SIZE_MIN)
                onPrefsChange(prefs.copy(fontSize = next.roundToStep()))
            },
            enabled = current > FONT_SIZE_MIN,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease font size")
        }
        Text(
            text = "${(current * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        OutlinedIconButton(
            onClick = {
                val next = (current + FONT_SIZE_STEP).coerceAtMost(FONT_SIZE_MAX)
                onPrefsChange(prefs.copy(fontSize = next.roundToStep()))
            },
            enabled = current < FONT_SIZE_MAX,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase font size")
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun FontFamilyRow(prefs: EpubPreferences, onPrefsChange: (EpubPreferences) -> Unit) {
    val current = prefs.fontFamily
    SectionLabel("Font")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FontChip("Default", null, current, prefs, onPrefsChange, ComposeFontFamily.Default)
        FontChip("Serif", FontFamily.SERIF, current, prefs, onPrefsChange, ComposeFontFamily.Serif)
        FontChip(
            "Sans",
            FontFamily.SANS_SERIF,
            current,
            prefs,
            onPrefsChange,
            ComposeFontFamily.SansSerif,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
private fun FontChip(
    label: String,
    family: FontFamily?,
    current: FontFamily?,
    prefs: EpubPreferences,
    onPrefsChange: (EpubPreferences) -> Unit,
    previewFont: ComposeFontFamily,
) {
    FilterChip(
        selected = current == family,
        onClick = { onPrefsChange(prefs.copy(fontFamily = family)) },
        label = { Text(label, fontFamily = previewFont) },
    )
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun LineHeightRow(prefs: EpubPreferences, onPrefsChange: (EpubPreferences) -> Unit) {
    val current = prefs.lineHeight?.toFloat() ?: LINE_HEIGHT_DEFAULT
    SectionLabel("Line spacing")
    Slider(
        value = current.coerceIn(LINE_HEIGHT_MIN, LINE_HEIGHT_MAX),
        onValueChange = { value ->
            // Advanced typography (line height) only applies when publisher styles are off.
            onPrefsChange(prefs.copy(lineHeight = value.toDouble(), publisherStyles = false))
        },
        valueRange = LINE_HEIGHT_MIN..LINE_HEIGHT_MAX,
        steps = LINE_HEIGHT_STEPS,
    )
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun PageMarginsRow(prefs: EpubPreferences, onPrefsChange: (EpubPreferences) -> Unit) {
    val current = prefs.pageMargins?.toFloat() ?: PAGE_MARGINS_DEFAULT
    SectionLabel("Margins")
    Slider(
        value = current.coerceIn(PAGE_MARGINS_MIN, PAGE_MARGINS_MAX),
        onValueChange = { value -> onPrefsChange(prefs.copy(pageMargins = value.toDouble())) },
        valueRange = PAGE_MARGINS_MIN..PAGE_MARGINS_MAX,
        steps = PAGE_MARGINS_STEPS,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
private fun PageModeRow(prefs: EpubPreferences, onPrefsChange: (EpubPreferences) -> Unit) {
    val scroll = prefs.scroll ?: false
    SectionLabel("Page mode")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !scroll,
            onClick = { onPrefsChange(prefs.copy(scroll = false)) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Paginated")
        }
        SegmentedButton(
            selected = scroll,
            onClick = { onPrefsChange(prefs.copy(scroll = true)) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("Scroll")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Rounds a multiplier to the nearest [FONT_SIZE_STEP] so repeated steps stay aligned. */
private fun Double.roundToStep(): Double = (this / FONT_SIZE_STEP).roundToInt() * FONT_SIZE_STEP
