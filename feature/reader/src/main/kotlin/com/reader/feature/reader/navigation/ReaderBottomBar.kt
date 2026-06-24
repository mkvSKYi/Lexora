package com.reader.feature.reader.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Bottom progress bar for the reader. Shows the current chapter title and reading percent,
 * with a draggable scrubber that seeks through the book.
 *
 * While the user drags the [Slider] the local [drag] value drives both the thumb and the
 * percent label, so the user sees the target they are heading to. The actual seek is committed
 * only once, on release ([Slider.onValueChangeFinished]) — never on every drag tick — so the
 * navigator is asked to jump a single time and the percent does not thrash mid-drag.
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
    // Non-null only while a drag is in progress; holds the in-flight target fraction.
    var drag by remember { mutableStateOf<Float?>(null) }
    val displayValue = drag ?: progression

    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = chapterTitle ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                )
                Text(text = "${(displayValue * 100).roundToInt()}%")
            }
            Slider(
                value = displayValue,
                valueRange = 0f..1f,
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let(onSeek)
                    drag = null
                },
            )
        }
    }
}
