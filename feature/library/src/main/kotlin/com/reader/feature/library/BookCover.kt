package com.reader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.reader.core.data.model.Book
import java.io.File
import kotlin.math.absoluteValue

/** A two-color gradient for a generated cover, stored as ARGB longs so it is plain-testable. */
data class CoverGradient(val top: Long, val bottom: Long)

// Curated vibrant violet→cyan→magenta family — deep enough that white text stays legible.
private val COVER_GRADIENTS = listOf(
    CoverGradient(0xFF6D5DF6, 0xFF8E54E9),
    CoverGradient(0xFF4776E6, 0xFF2EC6C4),
    CoverGradient(0xFFB24592, 0xFF6D5DF6),
    CoverGradient(0xFF1A2980, 0xFF26D0CE),
    CoverGradient(0xFFEE5D8A, 0xFFA24BCF),
    CoverGradient(0xFF2BC0E4, 0xFF3A6073),
    CoverGradient(0xFFF7971E, 0xFFCB356B),
    CoverGradient(0xFF11998E, 0xFF38EF7D),
)

/** Deterministic: a given seed always maps to the same handsome gradient. */
fun coverPalette(seed: String): CoverGradient {
    val index = (seed.hashCode().toLong().absoluteValue % COVER_GRADIENTS.size).toInt()
    return COVER_GRADIENTS[index]
}

/**
 * Renders a book's cover: the real image (with a legibility scrim) when present, otherwise a
 * generated gradient with a large monogram. Title/author live below the card, not on the cover.
 */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, monogramSize: TextUnit = 96.sp) {
    val coverPath = book.coverPath
    Box(modifier = modifier, contentAlignment = Alignment.BottomStart) {
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scrim(),
            )
        } else {
            val gradient = coverPalette(book.title.ifBlank { book.id.toString() })
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(gradient.top), Color(gradient.bottom)),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                    ),
            ) {
                // A large faint monogram as cover art; the title/author live below the card.
                Text(
                    text = book.title.firstOrNull()?.uppercase() ?: "•",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = monogramSize,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** A soft bottom dark scrim so overlaid text and progress stay legible on any cover. */
private fun Modifier.scrim(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            startY = size.height * 0.42f,
            endY = size.height,
        ),
    )
}
