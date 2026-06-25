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
private val PageShade = Color(0xFFD9CCAF)
private val InkBrown = Color(0xFF402A1B)
private val Cheek = Color(0xFFFF8FB1)
private val Ribbon = Color(0xFFEF5F4C)

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
    val bookW = s * 0.60f
    val bookH = s * 0.70f
    val left = cx - bookW / 2f
    val top = s * 0.13f
    val bottom = top + bookH
    val right = left + bookW
    val rad = s * 0.10f

    // Soft contact shadow.
    drawOval(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(cx - bookW * 0.44f, bottom + s * 0.01f),
        size = Size(bookW * 0.88f, s * 0.06f),
    )

    // Little feet poking out the bottom.
    val footW = s * 0.13f
    val footH = s * 0.07f
    listOf(-1f, 1f).forEach { side ->
        drawRoundRectCompat(
            color = AuroraDeep,
            left = cx + side * s * 0.15f - footW / 2f,
            top = bottom - footH * 0.45f,
            width = footW,
            height = footH,
            radius = footH / 2f,
        )
    }

    // Page block peeking out the right edge (drawn first, so the cover overlaps its left side).
    val pageW = s * 0.10f
    drawRoundRectCompat(
        color = PageCream,
        left = right - pageW,
        top = top + s * 0.03f,
        width = pageW,
        height = bookH - s * 0.06f,
        radius = s * 0.03f,
    )
    listOf(0.22f, 0.40f, 0.58f, 0.76f).forEach { fr ->
        drawLine(
            color = PageShade,
            start = Offset(right - pageW + s * 0.012f, top + bookH * fr),
            end = Offset(right - s * 0.018f, top + bookH * fr),
            strokeWidth = s * 0.009f,
            cap = StrokeCap.Round,
        )
    }

    // Bookmark ribbon hanging from the top.
    val bx = right - s * 0.22f
    val ribW = s * 0.08f
    val ribbon = Path().apply {
        moveTo(bx, top - s * 0.05f)
        lineTo(bx + ribW, top - s * 0.05f)
        lineTo(bx + ribW, top + s * 0.15f)
        lineTo(bx + ribW / 2f, top + s * 0.09f)
        lineTo(bx, top + s * 0.15f)
        close()
    }
    drawPath(ribbon, color = Ribbon)

    // Book cover (amber), slightly narrower so the page block stays visible on the right.
    val coverW = bookW - pageW * 0.55f
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(AuroraAccent, AuroraAccentSoft, AuroraDeep), startY = top, endY = bottom),
        topLeft = Offset(left, top),
        size = Size(coverW, bookH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rad, rad),
    )

    // Spine binding on the left edge: a darker band + groove line.
    drawRoundRectCompat(
        color = AuroraDeep,
        left = left,
        top = top,
        width = s * 0.10f,
        height = bookH,
        radius = rad,
    )
    drawLine(
        color = AuroraDeep,
        start = Offset(left + s * 0.115f, top + s * 0.05f),
        end = Offset(left + s * 0.115f, bottom - s * 0.05f),
        strokeWidth = s * 0.012f,
        cap = StrokeCap.Round,
    )

    // Face, centred on the amber cover (between spine and pages).
    val faceCx = left + s * 0.10f + (coverW - s * 0.10f) / 2f
    val eyeY = top + bookH * 0.42f
    val eyeDX = s * 0.115f
    val eyeR = s * 0.055f
    listOf(-1f, 1f).forEach { side ->
        val ex = faceCx + side * eyeDX
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
            center = Offset(faceCx + side * s * 0.175f, eyeY + s * 0.055f),
        )
    }

    // Mouth.
    val mouthY = eyeY + s * 0.10f
    when (mood) {
        MascotMood.HAPPY -> {
            val mouth = Path().apply {
                moveTo(faceCx - s * 0.05f, mouthY)
                quadraticBezierTo(faceCx, mouthY + s * 0.08f, faceCx + s * 0.05f, mouthY)
                close()
            }
            drawPath(mouth, color = InkBrown)
        }
        MascotMood.SLEEPY -> drawCircle(InkBrown.copy(alpha = 0.6f), radius = s * 0.012f, center = Offset(faceCx, mouthY))
        else -> drawArc(
            color = InkBrown,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(faceCx - s * 0.05f, mouthY - s * 0.04f),
            size = Size(s * 0.10f, s * 0.07f),
            style = Stroke(width = s * 0.02f, cap = StrokeCap.Round),
        )
    }

    // A few "Z"s when sleepy.
    if (mood == MascotMood.SLEEPY) {
        drawZ(Offset(right + s * 0.00f, top - s * 0.02f), s * 0.06f)
        drawZ(Offset(right + s * 0.09f, top - s * 0.14f), s * 0.085f)
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
