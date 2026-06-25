package com.reader.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.AuroraAccentSoft
import kotlin.math.sin
import kotlin.random.Random

private val ConfettiColors = listOf(
    AuroraAccent,
    AuroraAccentSoft,
    Color(0xFFFFD479), // warm gold
    Color(0xFF5BE9C8), // mint
    Color(0xFFFF8FB1), // pink
    Color.White,
)

private data class ConfettiPiece(
    val startX: Float,
    val delay: Float,
    val swingAmp: Float,
    val swings: Float,
    val widthDp: Float,
    val heightDp: Float,
    val spinTurns: Float,
    val baseRotation: Float,
    val color: Color,
)

/**
 * A one-shot confetti burst that rains down across [modifier]'s bounds and fades out. Plays once
 * when it enters composition — drop it in an overlay on a celebration moment.
 */
@Composable
fun Confetti(
    modifier: Modifier = Modifier,
    pieceCount: Int = 70,
    durationMillis: Int = 2400,
) {
    val pieces = remember {
        List(pieceCount) {
            ConfettiPiece(
                startX = Random.nextFloat(),
                delay = Random.nextFloat() * 0.25f,
                swingAmp = 0.02f + Random.nextFloat() * 0.06f,
                swings = 1f + Random.nextFloat() * 2f,
                widthDp = 6f + Random.nextFloat() * 6f,
                heightDp = 10f + Random.nextFloat() * 8f,
                spinTurns = 1f + Random.nextFloat() * 3f,
                baseRotation = Random.nextFloat() * 360f,
                color = ConfettiColors[Random.nextInt(ConfettiColors.size)],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis, easing = LinearEasing)) }

    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val margin = 60f
        pieces.forEach { p ->
            val tt = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (tt <= 0f) return@forEach
            val fall = tt * tt // gravity-like acceleration
            val y = -margin + (size.height + margin * 2) * fall
            val x = size.width * p.startX +
                sin(tt * p.swings * 2f * Math.PI.toFloat()) * p.swingAmp * size.width
            val alpha = if (tt < 0.82f) 1f else (1f - tt) / 0.18f
            val w = p.widthDp * density
            val h = p.heightDp * density
            rotate(degrees = p.baseRotation + p.spinTurns * tt * 360f, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(x - w / 2f, y - h / 2f),
                    size = Size(w, h),
                )
            }
        }
    }
}
