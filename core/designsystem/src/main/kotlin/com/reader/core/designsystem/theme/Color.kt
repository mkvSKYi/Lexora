package com.reader.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFF4C5BD4),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1A1B21),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF1A2683),
    secondary = Color(0xFFC2C5DD),
    onSecondary = Color(0xFF2B2F42),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
)
