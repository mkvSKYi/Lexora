package com.reader.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal val AuroraAccent = Color(0xFF9B8CFF)
internal val AuroraAccentSoft = Color(0xFF6D5DF6)

@Composable
fun DashboardScreen(
    onStartReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(state = state, onStartReview = onStartReview, modifier = modifier)
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onStartReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is DashboardUiState.Loading -> Unit
            is DashboardUiState.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                item {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item { StreakCard(state) }
                item { WordsCard(state.words, onStartReview) }
                item { BooksCard(state.books) }
            }
        }
    }
}

@Composable
private fun StreakCard(state: DashboardUiState.Content) {
    DashboardCard {
        if (state.hasActivity) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${state.streak}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = AuroraAccent,
                    )
                    Text(
                        text = "day streak",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                text = "Start reading to build your streak",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Reading, saving a word, or reviewing one keeps the day lit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        HeatmapGrid(state.heatmap)
    }
}

@Composable
private fun HeatmapGrid(cells: List<HeatCell>) {
    val weeks = cells.chunked(7) // 13 columns of 7 (Mon..Sun)
    val gap = 3.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = weeks.size.coerceAtLeast(1)
        val cell = (maxWidth - gap * (columns - 1)) / columns
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(cell * 7 + gap * 6),
        ) {
            val gapPx = gap.toPx()
            val cellPx = cell.toPx()
            val radius = CornerRadius(cellPx * 0.25f, cellPx * 0.25f)
            weeks.forEachIndexed { col, week ->
                week.forEachIndexed { row, c ->
                    drawRoundRect(
                        color = heatColor(c),
                        topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

private fun heatColor(cell: HeatCell): Color = when {
    cell.isFuture -> Color.White.copy(alpha = 0.03f)
    cell.level == 0 -> Color.White.copy(alpha = 0.07f)
    cell.level == 1 -> AuroraAccent.copy(alpha = 0.30f)
    cell.level == 2 -> AuroraAccent.copy(alpha = 0.50f)
    cell.level == 3 -> AuroraAccent.copy(alpha = 0.75f)
    else -> AuroraAccent
}

@Composable
private fun WordsCard(words: WordStats, onStartReview: () -> Unit) {
    DashboardCard {
        Text(
            text = "Vocabulary",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatColumn(value = words.total, label = "Saved")
            StatColumn(value = words.learned, label = "Learned")
            StatColumn(value = words.due, label = "Due")
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onStartReview,
            enabled = words.due > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (words.due > 0) "Review ${words.due}" else "Nothing to review")
        }
    }
}

@Composable
private fun BooksCard(books: BookStats) {
    DashboardCard {
        Text(
            text = "Books",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatColumn(value = books.inProgress, label = "In progress")
            StatColumn(value = books.finished, label = "Finished")
        }
    }
}

@Composable
private fun StatColumn(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AuroraAccent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
