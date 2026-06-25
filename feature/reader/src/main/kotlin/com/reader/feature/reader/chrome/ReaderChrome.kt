package com.reader.feature.reader.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Height of the invisible tap-target strip across the very top edge of the screen.
 * It spans the status-bar region plus a small band just below it, above where the EPUB
 * text renders, so tapping it toggles chrome visibility without reaching the navigator's
 * text (and therefore never resolves a word).
 */
private val REVEAL_STRIP_HEIGHT = 56.dp

/** Dark translucent "glass" used by the floating bars — legible over light or dark pages. */
private val GLASS = Color(0xFF15151B).copy(alpha = 0.86f)

/** Aurora accent shared with the rest of the app. */
private val ACCENT = Color(0xFF9B8CFF)

/**
 * Auto-hiding reader chrome. Lays [content] full-screen and overlays a floating top bar
 * (back + TOC + "Aa") plus a [bottomBar] slot, both driven by [visible].
 *
 * Reveal/auto-hide policy lives in the caller (`ReaderScreen`): this composable only renders
 * the bars for the given [visible] state and exposes [onRevealStripTap].
 *
 * @param visible whether the bars are currently shown.
 * @param onBack invoked by the back navigation icon.
 * @param onToc invoked by the TOC action (opens the table-of-contents sheet).
 * @param onAa invoked by the "Aa" action (opens reading-appearance settings).
 * @param onRevealStripTap invoked when the top-edge reveal strip is tapped.
 * @param bottomBar bottom slot, anchored to the bottom edge under the same [visible] state.
 * @param content the reader content (navigator + overlays), fills the box.
 */
@Composable
fun ReaderChrome(
    visible: Boolean,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAa: () -> Unit,
    onRevealStripTap: () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        // Thin top-edge tap target. Always present (even when the bars are hidden) so a tap there
        // re-reveals the chrome. Sits above the bars and over the status-bar strip.
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars),
            enter = slideInVertically { fullHeight -> -fullHeight } + fadeIn(),
            exit = slideOutVertically { fullHeight -> -fullHeight } + fadeOut(),
        ) {
            // Floating translucent "glass" bar — dark regardless of the reading theme so the
            // controls stay legible over a white sepia/light page too.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .shadow(10.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp)),
                color = GLASS,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToc) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Table of contents",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = Color.White,
                        )
                    }
                    TextButton(onClick = onAa) {
                        Text("Aa", color = ACCENT, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
            exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut(),
        ) {
            bottomBar()
        }
    }
}
