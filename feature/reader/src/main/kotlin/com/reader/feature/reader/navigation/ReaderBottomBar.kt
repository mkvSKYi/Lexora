package com.reader.feature.reader.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Dark translucent "glass" for the floating bottom bar — legible over light or dark pages. */
private val GLASS = Color(0xFF15151B).copy(alpha = 0.86f)

/** Aurora accent shared with the rest of the app. */
private val ACCENT = Color(0xFF9B8CFF)

/**
 * Floating bottom progress bar for the reader. Shows the current chapter title and reading percent,
 * with a draggable scrubber that seeks through the book.
 *
 * While the user drags the [Slider] the local [drag] value drives both the thumb and the percent
 * label; the actual seek is committed only once, on release ([Slider.onValueChangeFinished]).
 *
 * @param progression current reading progression in the book, 0..1.
 * @param chapterTitle current chapter title, or null when unknown.
 * @param onSeek invoked with the target fraction (0..1) when the user releases the scrubber.
 */
@Composable
fun ReaderBottomBar(
    progression: Float,
    chapterTitle: String?,
    onSeek: (Float) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val displayValue = drag ?: progression

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(10.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp)),
        color = GLASS,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapterTitle ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                )
                Text(
                    text = "${(displayValue * 100).roundToInt()}%",
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Slider(
                value = displayValue,
                valueRange = 0f..1f,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let(onSeek)
                    drag = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = ACCENT,
                    activeTrackColor = ACCENT,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
        }
    }
}
