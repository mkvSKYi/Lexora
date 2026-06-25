package com.reader.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.AuroraAccentSoft

private val XpGold = Color(0xFFFFD479)
private val XpAmber = Color(0xFFFFA63D)

/**
 * A game-style XP bar: a level badge + a gold progress bar that fills as experience drips in.
 * [progress] is 0..1 within the current level; it animates whenever XP is gained.
 */
@Composable
fun XpBar(
    level: Int,
    progress: Float,
    xpIntoLevel: Int,
    xpForLevel: Int,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "xpFill",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Level badge.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(AuroraAccentSoft, AuroraAccent))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LVL", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("$level", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Level $level",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$xpIntoLevel / $xpForLevel XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = XpAmber,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                if (animated > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animated)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(XpGold, XpAmber)))
                            .padding(top = 3.dp),
                    ) {
                        // Subtle top highlight for a glossy, game-y fill.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .padding(horizontal = 8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}
