package com.reader.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import com.reader.core.designsystem.components.AuroraButton
import com.reader.core.designsystem.components.DailyGoalRing
import com.reader.core.designsystem.components.XpBar
import com.reader.core.designsystem.theme.LexHairline
import com.reader.core.data.xp.LexoraXp
import com.reader.core.designsystem.mascot.LexoraMascot
import com.reader.core.designsystem.mascot.MascotMood
import com.reader.core.designsystem.motion.AnimatedCount
import com.reader.core.designsystem.motion.AppearOnce
import com.reader.core.designsystem.motion.Confetti
import com.reader.core.designsystem.motion.pulse
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.AuroraAccentSoft
import com.reader.core.designsystem.theme.AuroraDeep
import com.reader.core.designsystem.theme.Literata

@Composable
fun DashboardScreen(
    onStartReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var celebrateKey by remember { mutableIntStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.celebrateStreak.collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            celebrateKey++
            celebrating = true
            delay(4000)
            celebrating = false
        }
    }
    // Lexi reacts to your state: cheers on a milestone, reads when you've been active today,
    // and naps when you haven't.
    val todayActive = (state as? DashboardUiState.Content)?.todayActive == true
    val mascotMood = when {
        celebrating -> MascotMood.HAPPY
        todayActive -> MascotMood.READING
        else -> MascotMood.SLEEPY
    }
    DashboardContent(
        state = state,
        onStartReview = onStartReview,
        celebrateKey = celebrateKey,
        mascotMood = mascotMood,
        modifier = modifier,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onStartReview: () -> Unit,
    modifier: Modifier = Modifier,
    celebrateKey: Int = 0,
    mascotMood: MascotMood = MascotMood.IDLE,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Aurora glow bleeding down from the top, the same signature the library uses.
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AuroraAccentSoft.copy(alpha = 0.28f),
                                AuroraAccent.copy(alpha = 0.07f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            when (state) {
                is DashboardUiState.Loading -> Unit
                is DashboardUiState.Content -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    item { AppearOnce(delayMillis = 0) { LexiHero(mascotMood, state) } }
                    item { AppearOnce(delayMillis = 50) { XpRow(state.totalXp) } }
                    item { AppearOnce(delayMillis = 90) { StreakHero(state) } }
                    item { AppearOnce(delayMillis = 0) { DailyGoalCard(state) } }
                    item { AppearOnce(delayMillis = 0) { VocabularyCard(state.words, onStartReview) } }
                    item { AppearOnce(delayMillis = 0) { BooksCard(state.books) } }
                }
            }

            if (celebrateKey > 0) {
                key(celebrateKey) { Confetti(modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun LexiHero(mascotMood: MascotMood, state: DashboardUiState.Content) {
    Column {
        Text(
            text = todayDateLabel(),
            color = AuroraAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = greeting(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LexoraMascot(mood = mascotMood, modifier = Modifier.size(74.dp))
            SpeechBubble(
                text = lexiMessage(state),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun todayDateLabel(): String {
    val now = java.time.LocalDate.now()
    val dow = now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    val month = now.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
    return "$dow, ${now.dayOfMonth} $month".uppercase(java.util.Locale.ENGLISH)
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun lexiMessage(state: DashboardUiState.Content): String = when {
    state.words.due > 0 -> "${state.words.due} word${if (state.words.due == 1) "" else "s"} ready to review!"
    !state.todayActive -> "Let's read a little today 📖"
    state.streak > 0 -> "${state.streak}-day streak — keep it lit! 🔥"
    else -> "Tap a word while reading to save it."
}

@Composable
private fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val bubble = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .drawBehind {
                val tw = 9.dp.toPx()
                val cy = size.height / 2f
                val tail = Path().apply {
                    moveTo(0f, cy)
                    lineTo(tw, cy - tw)
                    lineTo(tw, cy + tw)
                    close()
                }
                drawPath(tail, color = bubble)
            }
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bubble)
            .border(1.dp, LexHairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun XpRow(totalXp: Int) {
    val info = LexoraXp.levelInfo(totalXp)
    GradientCard {
        XpBar(
            level = info.level,
            progress = info.progress,
            xpIntoLevel = info.xpIntoLevel,
            xpForLevel = info.xpForLevel,
        )
    }
}

/** The centrepiece: a glowing gradient hero with the streak number + the activity heatmap. */
@Composable
private fun StreakHero(state: DashboardUiState.Content) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(AuroraDeep, AuroraAccentSoft, AuroraAccent),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp).pulse(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    if (state.hasActivity) {
                        AnimatedCount(
                            target = state.streak.toInt(),
                            style = TextStyle(
                                fontFamily = Literata,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                shadow = Shadow(Color.White.copy(alpha = 0.55f), blurRadius = 26f),
                            ),
                        )
                        Text(
                            text = "day streak",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Text(
                            text = "Start your streak",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Read, save or review a word to light up today.",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.monthLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                HeatmapLegend()
            }
            HeatmapGrid(state.heatmap)
        }
    }
}

@Composable
private fun HeatmapGrid(cells: List<HeatCell>) {
    val weeks = cells.chunked(7) // each chunk is a Mon..Sun week → a calendar row
    val gap = 5.dp
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(
                    text = d,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                week.forEach { c ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.12f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(heatCellColor(c)),
                    )
                }
            }
        }
    }
}

/** Cells sit on the purple gradient, so they shade from translucent-white up to solid white. */
private fun heatCellColor(cell: HeatCell): Color = when {
    cell.muted -> Color(0xFF231C12).copy(alpha = 0.55f) // recessed well, reads as "outside the month"
    cell.level == 0 -> Color(0xFF231C12)                // empty in-month day: a dark warm well
    cell.level == 1 -> Color.White.copy(alpha = 0.45f)
    cell.level == 2 -> Color.White.copy(alpha = 0.65f)
    cell.level == 3 -> Color.White.copy(alpha = 0.82f)
    else -> Color.White
}

@Composable
private fun HeatmapLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Less", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
        Spacer(Modifier.width(4.dp))
        listOf(0.14f, 0.40f, 0.60f, 0.80f, 1f).forEach { a ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(9.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = a)),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("More", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

@Composable
private fun DailyGoalCard(state: DashboardUiState.Content) {
    GradientCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DailyGoalRing(progress = state.goalProgress, modifier = Modifier.size(78.dp)) {
                if (state.goalReached) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = AuroraAccent, modifier = Modifier.size(32.dp))
                } else {
                    Text(
                        text = "${state.todayActions}/${state.dailyGoal}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                SectionLabel(icon = Icons.Filled.Bolt, text = "DAILY GOAL")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.goalReached) {
                        "Goal complete!"
                    } else {
                        "${state.dailyGoal - state.todayActions} more words to go"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Save or review words to fill the ring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VocabularyCard(words: WordStats, onStartReview: () -> Unit) {
    GradientCard {
        SectionLabel(icon = Icons.Filled.Style, text = "VOCABULARY")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(icon = Icons.Filled.AutoStories, value = words.total, label = "Saved")
            Stat(icon = Icons.Filled.School, value = words.learned, label = "Learned")
            Stat(icon = Icons.Filled.Bolt, value = words.due, label = "Due")
        }
        Spacer(Modifier.height(18.dp))
        ReviewButton(due = words.due, onClick = onStartReview)
    }
}

@Composable
private fun BooksCard(books: BookStats) {
    GradientCard {
        SectionLabel(icon = Icons.Filled.MenuBook, text = "BOOKS")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(icon = Icons.Filled.AutoStories, value = books.inProgress, label = "In progress")
            Stat(icon = Icons.Filled.CheckCircle, value = books.finished, label = "Finished")
        }
    }
}

@Composable
private fun ReviewButton(due: Int, onClick: () -> Unit) {
    AuroraButton(
        text = if (due > 0) "Review $due now" else "Nothing to review",
        onClick = onClick,
        enabled = due > 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Stat(icon: ImageVector, value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = AuroraAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        AnimatedCount(
            target = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AuroraAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = AuroraAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun GradientCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, LexHairline, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column(content = content)
    }
}
