package com.reader.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Ember" — Lexora's warm, literary palette: a honey/amber accent (the colour of reading light) on a
 * warm espresso base, balanced by a cool teal. Deliberately not the generic purple.
 *
 * The historical `Aurora*` names are kept so existing call sites stay valid; the values are amber.
 */
val AuroraDeep = Color(0xFFB9741F)       // deep ember
val AuroraAccentSoft = Color(0xFFF0A53A) // amber
val AuroraAccent = Color(0xFFFFC368)     // honey (bright accent)

/** Cool secondary, used sparingly for balance (goal ring, calm accents). */
val LexTeal = Color(0xFF53C7A6)
val LexTealDeep = Color(0xFF2F8E78)

/** Warm surfaces + hairline borders that give depth without heavy gradients. */
val LexBase = Color(0xFF16130F)
val LexSurface = Color(0xFF221D16)
val LexSurfaceHigh = Color(0xFF2C261D)
val LexHairline = Color(0x1FFFFFFF)      // white @ 12% — crisp 1px card borders
val LexTextHigh = Color(0xFFF5EFE4)
val LexTextMuted = Color(0xFFA99E8C)

internal val LightColors = lightColorScheme(
    primary = AuroraAccentSoft,
    onPrimary = Color(0xFF3A2400),
    secondary = LexTeal,
    background = Color(0xFFFCF8F1),
    onBackground = Color(0xFF1C1813),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1813),
    surfaceVariant = Color(0xFFF1EADC),
    onSurfaceVariant = Color(0xFF6B6052),
)

internal val DarkColors = darkColorScheme(
    primary = AuroraAccent,
    onPrimary = Color(0xFF3A2400),
    secondary = LexTeal,
    onSecondary = Color(0xFF06291F),
    background = LexBase,
    onBackground = LexTextHigh,
    surface = LexBase,
    onSurface = LexTextHigh,
    surfaceVariant = LexSurface,
    onSurfaceVariant = LexTextMuted,
    outline = LexHairline,
)
