package com.reader.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.core.data.model.SavedWord
import androidx.compose.foundation.border
import com.reader.core.designsystem.motion.AnimatedCount
import com.reader.core.designsystem.motion.AppearOnce
import com.reader.core.designsystem.motion.Confetti
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.LexHairline
import com.reader.core.designsystem.theme.LexTeal
import com.reader.core.designsystem.theme.Literata
import com.reader.feature.translation.WordDictionarySheet
import com.reader.feature.translation.WordLookupViewModel
import java.text.DateFormat
import java.util.Date

// Warm "learned" accent — a sage-teal that sits in the Ember palette instead of a clashing green.
private val Learned = LexTeal

@Composable
fun SavedWordsScreen(
    onBack: () -> Unit,
    onStartReview: () -> Unit,
    viewModel: SavedWordsViewModel = hiltViewModel(),
) {
    val wordVm: WordLookupViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val wordState by wordVm.lookupState.collectAsStateWithLifecycle()
    val canSpeak by wordVm.ttsAvailable.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    // Bumped each time a word graduates to "learned" so the confetti replays as a reward.
    var celebrateKey by remember { mutableIntStateOf(0) }

    SavedWordsContent(
        uiState = uiState,
        filter = filter,
        celebrateKey = celebrateKey,
        onBack = onBack,
        onStartReview = onStartReview,
        onWordTap = { wordVm.onWord(it.term) },
        onSetFilter = viewModel::setFilter,
        onToggleLearned = { id, learned ->
            viewModel.toggleLearned(id, learned)
            if (learned) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                celebrateKey++
            }
        },
        onDelete = viewModel::delete,
    )

    wordState?.let { state ->
        WordDictionarySheet(
            state = state,
            onSave = { _, _ -> },
            onDismiss = { wordVm.dismiss() },
            showSave = false,
            canSpeak = canSpeak,
            onSpeak = wordVm::speak,
        )
    }
}

@Composable
fun SavedWordsContent(
    uiState: SavedWordsUiState,
    filter: SavedWordsFilter,
    celebrateKey: Int,
    onBack: () -> Unit,
    onStartReview: () -> Unit,
    onWordTap: (SavedWord) -> Unit,
    onSetFilter: (SavedWordsFilter) -> Unit,
    onToggleLearned: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Aurora glow behind the header.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AuroraAccent.copy(alpha = 0.22f),
                            AuroraAccent.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        when (uiState) {
            is SavedWordsUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AuroraAccent)
                }

            is SavedWordsUiState.Content ->
                if (uiState.totalCount == 0) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Header(onBack = onBack)
                        EmptySavedWords(modifier = Modifier.fillMaxSize())
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Header(onBack = onBack) }
                        if (uiState.dueCount > 0) {
                            item {
                                AppearOnce(delayMillis = 40) {
                                    ReviewEntryCard(dueCount = uiState.dueCount, onClick = onStartReview)
                                }
                            }
                        }
                        item {
                            AppearOnce(delayMillis = 90) {
                                StatsCard(learnedCount = uiState.learnedCount, totalCount = uiState.totalCount)
                            }
                        }
                        item { FilterChips(filter = filter, onSelect = onSetFilter) }
                        if (uiState.words.isEmpty()) {
                            item { NothingHereRow() }
                        } else {
                            itemsIndexed(uiState.words) { index, word ->
                                AppearOnce(delayMillis = 140 + index * 45) {
                                    SavedWordCard(
                                        word = word,
                                        onTap = { onWordTap(word) },
                                        onToggleLearned = { learned -> onToggleLearned(word.id, learned) },
                                        onDelete = { onDelete(word.id) },
                                    )
                                }
                            }
                        }
                    }
                }
        }

        if (celebrateKey > 0) {
            key(celebrateKey) { Confetti(modifier = Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Saved Words",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ReviewEntryCard(dueCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AuroraAccent.copy(alpha = 0.14f))
            .border(1.dp, AuroraAccent.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AuroraAccent.copy(alpha = 0.7f), AuroraAccent))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.School, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = "Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(text = "$dueCount due", style = MaterialTheme.typography.bodyMedium, color = AuroraAccent)
            }
        }
    }
}

@Composable
private fun StatsCard(learnedCount: Int, totalCount: Int) {
    val progress = if (totalCount > 0) learnedCount.toFloat() / totalCount else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, LexHairline, RoundedCornerShape(24.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedCount(
                target = learnedCount,
                style = MaterialTheme.typography.headlineMedium,
                color = AuroraAccent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "of $totalCount learned",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = AuroraAccent,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(
    filter: SavedWordsFilter,
    onSelect: (SavedWordsFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterEntry("All", SavedWordsFilter.ALL, filter, onSelect)
        FilterEntry("Learning", SavedWordsFilter.LEARNING, filter, onSelect)
        FilterEntry("Learned", SavedWordsFilter.LEARNED, filter, onSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterEntry(
    label: String,
    value: SavedWordsFilter,
    selected: SavedWordsFilter,
    onSelect: (SavedWordsFilter) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AuroraAccent.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun SavedWordCard(
    word: SavedWord,
    onTap: () -> Unit,
    onToggleLearned: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val learned = word.learned
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (learned) Learned.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                1.dp,
                if (learned) Learned.copy(alpha = 0.35f) else LexHairline,
                RoundedCornerShape(20.dp),
            )
            .clickable { onTap() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = word.term,
                    fontFamily = Literata,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(text = word.translation, style = MaterialTheme.typography.bodyLarge, color = AuroraAccent)
                word.contextSentence?.let { sentence ->
                    Text(
                        text = "“$sentence”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${word.bookTitle} · ${formatDate(word.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onToggleLearned(!word.learned) }) {
                Icon(
                    imageVector = if (word.learned) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (word.learned) "Mark as learning" else "Mark as learned",
                    tint = if (word.learned) Learned else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NothingHereRow() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Nothing here yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptySavedWords(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AuroraAccent.copy(alpha = 0.6f), AuroraAccent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No saved words yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap a word while reading and save it to build your vocabulary here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
