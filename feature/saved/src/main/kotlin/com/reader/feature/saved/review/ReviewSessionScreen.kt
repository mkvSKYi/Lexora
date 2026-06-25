package com.reader.feature.saved.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.core.data.model.SavedWord
import com.reader.core.data.review.ReviewGrade
import com.reader.core.designsystem.components.AuroraButton
import com.reader.core.designsystem.mascot.LexoraMascot
import com.reader.core.designsystem.mascot.MascotMood
import com.reader.core.designsystem.motion.AnimatedCount
import com.reader.core.designsystem.motion.AppearOnce
import com.reader.core.designsystem.motion.Confetti
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.AuroraAccentSoft

private val Accent = Color(0xFFFFC368)
private val LearnedGreen = Color(0xFF34C759)
private val AgainRed = Color(0xFFE5534B)

@Composable
fun ReviewSessionScreen(
    onDone: () -> Unit,
    viewModel: ReviewSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Aurora glow.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Accent.copy(alpha = 0.22f),
                            Accent.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        when (val s = state) {
            is ReviewSessionUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }

            is ReviewSessionUiState.Empty ->
                CenteredMessage(
                    title = "Nothing to review",
                    subtitle = "You're all caught up.",
                    onDone = onDone,
                )

            is ReviewSessionUiState.Summary ->
                SessionCelebration(reviewed = s.reviewed, learned = s.learned, onDone = onDone)

            is ReviewSessionUiState.Reviewing ->
                ReviewingContent(
                    state = s,
                    onReveal = viewModel::reveal,
                    onGrade = viewModel::grade,
                )
        }
    }
}

@Composable
private fun ReviewingContent(
    state: ReviewSessionUiState.Reviewing,
    onReveal: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (state.total > 0) state.position.toFloat() / state.total else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = Accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "${state.position} / ${state.total}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )

        FlashCard(
            card = state.card,
            revealed = state.revealed,
            onReveal = onReveal,
            modifier = Modifier.weight(1f),
        )

        if (state.revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradeButton(
                    label = "Again",
                    intervalLabel = state.againLabel,
                    color = AgainRed,
                    onClick = { onGrade(ReviewGrade.AGAIN) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Good",
                    intervalLabel = state.goodLabel,
                    color = Accent,
                    onClick = { onGrade(ReviewGrade.GOOD) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Easy",
                    intervalLabel = state.easyLabel,
                    color = LearnedGreen,
                    onClick = { onGrade(ReviewGrade.EASY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FlashCard(
    card: SavedWord,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = if (revealed) modifier else modifier.clickable { onReveal() }
    Card(
        modifier = cardModifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = card.term,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            card.contextSentence?.let { sentence ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "“$sentence”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                )
            }
            if (revealed) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = card.translation,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    textAlign = TextAlign.Center,
                )
            } else {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Tap to reveal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    intervalLabel: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = intervalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** The dopamine payoff: a trophy, rolling-up counts, a soft haptic, and a confetti rain. */
@Composable
private fun SessionCelebration(reviewed: Int, learned: Int, onDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Box(modifier = Modifier.fillMaxSize()) {
        AppearOnce(modifier = Modifier.align(Alignment.Center)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LexoraMascot(mood = MascotMood.HAPPY, modifier = Modifier.size(140.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Nice work!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Session complete",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    CelebrationStat(value = reviewed, label = "reviewed")
                    CelebrationStat(value = learned, label = "learned")
                }
                Spacer(Modifier.height(34.dp))
                AuroraButton(
                    text = "Done",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
        }
        Confetti(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun CelebrationStat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedCount(
            target = value,
            style = MaterialTheme.typography.headlineLarge,
            color = AuroraAccent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    subtitle: String?,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
        ) {
            Text("Done")
        }
    }
}
