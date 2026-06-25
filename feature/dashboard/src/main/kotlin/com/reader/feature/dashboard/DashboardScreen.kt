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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.core.designsystem.components.AuroraButton
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
    var mascotMood by remember { mutableStateOf(MascotMood.IDLE) }
    LaunchedEffect(Unit) {
        viewModel.celebrateStreak.collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            celebrateKey++
            mascotMood = MascotMood.HAPPY
            delay(4000)
            mascotMood = MascotMood.IDLE
        }
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
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
                ) {
                    item { AppearOnce(delayMillis = 0) { Header(mascotMood) } }
                    item { AppearOnce(delayMillis = 90) { StreakHero(state) } }
                    item { AppearOnce(delayMillis = 180) { VocabularyCard(state.words, onStartReview) } }
                    item { AppearOnce(delayMillis = 270) { BooksCard(state.books) } }
                }
            }

            if (celebrateKey > 0) {
                key(celebrateKey) { Confetti(modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun Header(mascotMood: MascotMood) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LexoraMascot(
            mood = mascotMood,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "YOUR PROGRESS",
                color = AuroraAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Today",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
        }
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
            .padding(24.dp),
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
                                fontSize = 46.sp,
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
            Spacer(Modifier.height(20.dp))
            HeatmapGrid(state.heatmap)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Last 13 weeks",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
                HeatmapLegend()
            }
        }
    }
}

@Composable
private fun HeatmapGrid(cells: List<HeatCell>) {
    val weeks = cells.chunked(7)
    val gap = 3.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = weeks.size.coerceAtLeast(1)
        val cell = (maxWidth - gap * (columns - 1)) / columns
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(cell * 7 + gap * 6),
        ) {
            val gapPx = gap.toPx()
            val cellPx = cell.toPx()
            val radius = CornerRadius(cellPx * 0.3f, cellPx * 0.3f)
            weeks.forEachIndexed { col, week ->
                week.forEachIndexed { row, c ->
                    drawRoundRect(
                        color = heatCellColor(c),
                        topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/** Cells sit on the purple gradient, so they shade from translucent-white up to solid white. */
private fun heatCellColor(cell: HeatCell): Color = when {
    cell.isFuture -> Color.White.copy(alpha = 0.05f)
    cell.level == 0 -> Color.White.copy(alpha = 0.14f)
    cell.level == 1 -> Color.White.copy(alpha = 0.40f)
    cell.level == 2 -> Color.White.copy(alpha = 0.60f)
    cell.level == 3 -> Color.White.copy(alpha = 0.80f)
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
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(AuroraAccentSoft.copy(alpha = 0.16f), AuroraAccent.copy(alpha = 0.03f)),
                    ),
                )
            }
            .padding(20.dp),
    ) {
        Column(content = content)
    }
}
