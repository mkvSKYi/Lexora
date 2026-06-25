package com.reader.core.designsystem.mascot

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.AuroraAccentSoft
import com.reader.core.designsystem.theme.AuroraDeep

/** Lexora's mascot moods drive its face + how bouncy it is. */
enum class MascotMood { IDLE, HAPPY, READING, SLEEPY }

private val PageCream = Color(0xFFFDF6E9)
private val PageShade = Color(0xFFE9DEC6)
private val InkBrown = Color(0xFF3A2E4D)
private val Cheek = Color(0xFFFF8FB1)
private val Ribbon = Color(0xFFFFC15E)

/**
 * "Lexi" — Lexora's book-creature mascot, drawn and animated entirely in Compose. It breathes and
 * blinks on its own; [mood] changes the face (and adds a celebratory bounce when HAPPY).
 */
@Composable
fun LexoraMascot(
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.IDLE,
) {
    val t = rememberInfiniteTransition(label = "mascot")

    val breathe by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breathe",
    )
    val bounce by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (mood == MascotMood.HAPPY) 360 else 1800), RepeatMode.Reverse),
        label = "bounce",
    )
    // Eyes: mostly open (1), with a quick blink dip every few seconds.
    val blink by t.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3400
                1f at 0
                1f at 2900
                0.08f at 3040
                1f at 3180
            },
        ),
        label = "blink",
    )

    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val cx = size.width / 2f
        val bob = if (mood == MascotMood.HAPPY) -s * 0.06f * bounce else -s * 0.02f * bounce
        val squash = 1f + (if (mood == MascotMood.HAPPY) 0.05f else 0.03f) * breathe

        withTransform({
            translate(left = 0f, top = bob)
            scale(scaleX = 1f / squash * 1f, scaleY = squash, pivot = Offset(cx, size.height * 0.86f))
        }) {
            drawBookCreature(cx = cx, s = s, mood = mood, blink = blink)
        }
    }
}

private fun DrawScope.drawBookCreature(cx: Float, s: Float, mood: MascotMood, blink: Float) {
    val bottomY = s * 0.86f
    val hw = s * 0.42f
    val humpY = s * 0.16f
    val valleyY = s * 0.30f

    // Soft contact shadow.
    drawOval(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(cx - hw * 0.75f, bottomY + s * 0.02f),
        size = Size(hw * 1.5f, s * 0.07f),
    )

    // Little feet.
    val footW = s * 0.12f
    val footH = s * 0.06f
    listOf(-1f, 1f).forEach { side ->
        drawRoundRectCompat(
            color = AuroraDeep,
            left = cx + side * s * 0.16f - footW / 2f,
            top = bottomY - footH * 0.4f,
            width = footW,
            height = footH,
            radius = footH / 2f,
        )
    }

    // Book body: two top "page" humps with a central spine valley.
    val body = Path().apply {
        moveTo(cx, bottomY)
        cubicTo(cx - hw * 0.55f, bottomY + s * 0.02f, cx - hw, bottomY - s * 0.18f, cx - hw, s * 0.52f)
        cubicTo(cx - hw, humpY + s * 0.04f, cx - hw * 0.78f, humpY, cx - hw * 0.40f, humpY + s * 0.02f)
        cubicTo(cx - hw * 0.18f, humpY + s * 0.04f, cx - hw * 0.06f, valleyY - s * 0.02f, cx, valleyY)
        cubicTo(cx + hw * 0.06f, valleyY - s * 0.02f, cx + hw * 0.18f, humpY + s * 0.04f, cx + hw * 0.40f, humpY + s * 0.02f)
        cubicTo(cx + hw * 0.78f, humpY, cx + hw, humpY + s * 0.04f, cx + hw, s * 0.52f)
        cubicTo(cx + hw, bottomY - s * 0.18f, cx + hw * 0.55f, bottomY + s * 0.02f, cx, bottomY)
        close()
    }
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(AuroraAccent, AuroraAccentSoft, AuroraDeep),
            startY = humpY,
            endY = bottomY,
        ),
    )

    // Page faces (cream) inset on each side of the spine — the "open book" read.
    val pageInset = s * 0.07f
    listOf(-1f, 1f).forEach { side ->
        val page = Path().apply {
            moveTo(cx + side * pageInset * 0.4f, valleyY + s * 0.02f)
            cubicTo(
                cx + side * hw * 0.2f, humpY + s * 0.10f,
                cx + side * hw * 0.62f, humpY + s * 0.08f,
                cx + side * (hw - pageInset), s * 0.50f,
            )
            cubicTo(
                cx + side * (hw - pageInset), bottomY - s * 0.22f,
                cx + side * hw * 0.5f, bottomY - s * 0.10f,
                cx + side * pageInset * 0.4f, bottomY - s * 0.08f,
            )
            close()
        }
        drawPath(page, color = if (side < 0) PageCream else PageShade)
    }

    // Spine.
    drawLine(
        color = AuroraDeep,
        start = Offset(cx, valleyY + s * 0.01f),
        end = Offset(cx, bottomY - s * 0.06f),
        strokeWidth = s * 0.03f,
        cap = StrokeCap.Round,
    )

    // Bookmark ribbon peeking from the top valley.
    val ribbon = Path().apply {
        moveTo(cx - s * 0.05f, valleyY - s * 0.02f)
        lineTo(cx + s * 0.05f, valleyY - s * 0.02f)
        lineTo(cx + s * 0.05f, valleyY + s * 0.14f)
        lineTo(cx, valleyY + s * 0.08f)
        lineTo(cx - s * 0.05f, valleyY + s * 0.14f)
        close()
    }
    drawPath(ribbon, color = Ribbon)

    // Face.
    val eyeY = s * 0.46f
    val eyeDX = s * 0.155f
    val eyeR = s * 0.058f
    listOf(-1f, 1f).forEach { side ->
        val ex = cx + side * eyeDX
        when (mood) {
            MascotMood.HAPPY -> drawArc( // ^_^ happy eyes
                color = InkBrown,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(ex - eyeR, eyeY - eyeR),
                size = Size(eyeR * 2f, eyeR * 2f),
                style = Stroke(width = s * 0.022f, cap = StrokeCap.Round),
            )
            MascotMood.SLEEPY -> drawLine(
                color = InkBrown,
                start = Offset(ex - eyeR, eyeY),
                end = Offset(ex + eyeR, eyeY),
                strokeWidth = s * 0.02f,
                cap = StrokeCap.Round,
            )
            else -> {
                // Open eye that squishes on blink.
                val h = eyeR * 2f * blink
                drawRoundRectCompat(
                    color = Color.White,
                    left = ex - eyeR,
                    top = eyeY - h / 2f,
                    width = eyeR * 2f,
                    height = h,
                    radius = eyeR,
                )
                if (blink > 0.5f) {
                    val look = if (mood == MascotMood.READING) eyeR * 0.5f else 0f
                    drawCircle(InkBrown, radius = eyeR * 0.5f, center = Offset(ex, eyeY + look))
                    drawCircle(Color.White, radius = eyeR * 0.18f, center = Offset(ex + eyeR * 0.18f, eyeY - eyeR * 0.2f + look))
                }
            }
        }
    }

    // Cheeks.
    listOf(-1f, 1f).forEach { side ->
        drawCircle(
            color = Cheek.copy(alpha = 0.55f),
            radius = s * 0.035f,
            center = Offset(cx + side * s * 0.24f, eyeY + s * 0.05f),
        )
    }

    // Mouth.
    val mouthY = eyeY + s * 0.10f
    when (mood) {
        MascotMood.HAPPY -> {
            val mouth = Path().apply {
                moveTo(cx - s * 0.05f, mouthY)
                quadraticBezierTo(cx, mouthY + s * 0.08f, cx + s * 0.05f, mouthY)
                close()
            }
            drawPath(mouth, color = InkBrown)
        }
        MascotMood.SLEEPY -> drawCircle(InkBrown.copy(alpha = 0.6f), radius = s * 0.012f, center = Offset(cx, mouthY))
        else -> drawArc(
            color = InkBrown,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - s * 0.05f, mouthY - s * 0.04f),
            size = Size(s * 0.10f, s * 0.07f),
            style = Stroke(width = s * 0.02f, cap = StrokeCap.Round),
        )
    }

    // A few "Z"s when sleepy.
    if (mood == MascotMood.SLEEPY) {
        drawZ(Offset(cx + hw * 0.7f, valleyY), s * 0.06f)
        drawZ(Offset(cx + hw * 0.95f, valleyY - s * 0.12f), s * 0.085f)
    }
}

private fun DrawScope.drawRoundRectCompat(color: Color, left: Float, top: Float, width: Float, height: Float, radius: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )
}

private fun DrawScope.drawZ(at: Offset, size: Float) {
    val p = Path().apply {
        moveTo(at.x, at.y)
        lineTo(at.x + size, at.y)
        lineTo(at.x, at.y + size)
        lineTo(at.x + size, at.y + size)
    }
    drawPath(p, color = AuroraAccent, style = Stroke(width = size * 0.16f, cap = StrokeCap.Round))
}
