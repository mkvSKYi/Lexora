package com.reader.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.reader.core.designsystem.R

@OptIn(ExperimentalTextApi::class)
private fun literata(weight: Int) = Font(
    resId = R.font.literata,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Lexora's signature display face: a literary serif used for headings + hero numbers. */
val Literata = FontFamily(literata(400), literata(500), literata(600), literata(700))

/**
 * Headings wear the literary serif (Literata); body and labels stay on the platform sans. This is
 * what gives Lexora a "book" identity distinct from generic Material apps.
 */
internal val ReaderTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = Literata, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = Literata, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Literata, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Literata, fontWeight = FontWeight.SemiBold),
    )
}
