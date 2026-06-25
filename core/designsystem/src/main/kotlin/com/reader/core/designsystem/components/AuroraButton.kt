package com.reader.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.AuroraAccentSoft
import com.reader.core.designsystem.theme.AuroraDeep

/**
 * A chunky, tactile button that sits on a darker "lip" and physically depresses onto it when
 * pressed — the signature Lexora press feel. Provide [content] for custom rows, or use the
 * text overload.
 */
@Composable
fun AuroraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    lip: Dp = 5.dp,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val drop by animateDpAsState(
        targetValue = if (pressed && enabled) lip else 0.dp,
        animationSpec = tween(40),
        label = "auroraButtonDrop",
    )

    val faceBrush = if (enabled) {
        Brush.verticalGradient(listOf(AuroraAccent, AuroraAccentSoft))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF33333C)))
    }
    val lipColor = if (enabled) AuroraDeep else Color(0xFF24242B)

    Box(modifier = modifier.height(height + lip)) {
        // The lip: same shape, darker, anchored to the bottom so it peeks below the resting face.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .align(Alignment.BottomCenter)
                .clip(shape)
                .background(lipColor),
        )
        // The face: rests at the top and drops onto the lip on press.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = drop)
                .clip(shape)
                .background(faceBrush)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .semantics(mergeDescendants = true) { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                content()
            }
        }
    }
}

@Composable
fun AuroraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AuroraButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
