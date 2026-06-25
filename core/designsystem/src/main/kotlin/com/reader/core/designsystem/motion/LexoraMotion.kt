package com.reader.core.designsystem.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A number that rolls up from zero to [target] when it first appears. */
@Composable
fun AnimatedCount(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    durationMillis: Int = 650,
) {
    var current by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(target) { current = target }
    val value by animateIntAsState(
        targetValue = current,
        animationSpec = tween(durationMillis),
        label = "animatedCount",
    )
    Text(text = "$value", modifier = modifier, style = style, color = color, fontWeight = fontWeight)
}

/**
 * Reveals [content] once with a gentle fade + spring-settled slide-up. Stagger siblings by passing
 * increasing [delayMillis] to get a cascade.
 */
@Composable
fun AppearOnce(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffectShown(delayMillis) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "appearAlpha")
    val offsetY by animateDpAsState(
        targetValue = if (shown) 0.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "appearOffset",
    )
    Box(modifier.offset(y = offsetY).alpha(alpha)) { content() }
}

/** A slow, infinite scale pulse — used to keep the streak flame alive. */
@Composable
fun Modifier.pulse(min: Float = 1f, max: Float = 1.12f, periodMillis: Int = 950): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(tween(periodMillis), RepeatMode.Reverse),
        label = "pulseScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
private fun LaunchedEffectShown(delayMillis: Int, onShown: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        onShown()
    }
}
