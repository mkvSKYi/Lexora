package com.reader.feature.reader.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Height of the invisible tap-target strip across the very top edge of the screen.
 * It spans the status-bar region plus a small band just below it, above where the EPUB
 * text renders, so tapping it toggles chrome visibility without reaching the navigator's
 * text (and therefore never resolves a word). The system owns the very top of this band
 * (notification shade), so the strip is tall enough to leave a comfortable tap target
 * below the status bar while staying out of the reading area.
 */
private val REVEAL_STRIP_HEIGHT = 56.dp

/**
 * Auto-hiding reader chrome. Lays [content] full-screen and overlays an animated top app
 * bar (back + "Aa") plus a [bottomBar] slot, both driven by [visible].
 *
 * Reveal/auto-hide policy lives in the caller (`ReaderScreen`): this composable only renders
 * the bars for the given [visible] state and exposes [onRevealStripTap], invoked when the
 * user taps the thin top-edge strip. Keeping the strip here guarantees it is laid out above
 * the bars and over the status-bar region, away from the navigator's word/sentence gestures.
 *
 * @param visible whether the bars are currently shown.
 * @param onBack invoked by the back navigation icon.
 * @param onAa invoked by the "Aa" action (opens reading-appearance settings).
 * @param onRevealStripTap invoked when the top-edge reveal strip is tapped.
 * @param bottomBar bottom slot, anchored to the bottom edge under the same [visible] state.
 * @param content the reader content (navigator + overlays), fills the box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderChrome(
    visible: Boolean,
    onBack: () -> Unit,
    onAa: () -> Unit,
    onRevealStripTap: () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        // Thin top-edge tap target. Always present (even when the bars are hidden) so a tap
        // there re-reveals the chrome. Sits above the bars and over the status-bar strip, so
        // it never forwards taps to the navigator text below it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(REVEAL_STRIP_HEIGHT)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onRevealStripTap() })
                },
        )

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { fullHeight -> -fullHeight },
            exit = slideOutVertically { fullHeight -> -fullHeight },
        ) {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onAa) {
                        Text("Aa")
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { fullHeight -> fullHeight },
            exit = slideOutVertically { fullHeight -> fullHeight },
        ) {
            bottomBar()
        }
    }
}
