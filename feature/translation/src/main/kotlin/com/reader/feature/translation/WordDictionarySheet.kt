package com.reader.feature.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.core.designsystem.components.AuroraButton
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.LexHairline
import com.reader.core.designsystem.theme.LexSurface
import com.reader.core.designsystem.theme.Literata

private val SHEET_H_PADDING = 24.dp
private val SHEET_V_PADDING = 20.dp
private val LOADING_MIN_HEIGHT = 160.dp
private val SECTION_SPACING = 16.dp
private const val COLLAPSED_DEFINITIONS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDictionarySheet(
    state: WordLookupState,
    onSave: (term: String, translation: String) -> Unit,
    onDismiss: () -> Unit,
    showSave: Boolean = true,
    canSpeak: Boolean = false,
    onSpeak: (String) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LexSurface,
    ) {
        when (state) {
            WordLookupState.Loading -> LoadingContent()
            is WordLookupState.Entry -> EntryContent(state, onSave, showSave, canSpeak, onSpeak)
            is WordLookupState.Machine -> MachineContent(state, onSave, showSave, canSpeak, onSpeak)
            is WordLookupState.Error -> ErrorContent(state)
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = LOADING_MIN_HEIGHT),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AuroraAccent)
        Text(
            text = "Looking up…",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryContent(
    state: WordLookupState.Entry,
    onSave: (term: String, translation: String) -> Unit,
    showSave: Boolean,
    canSpeak: Boolean,
    onSpeak: (String) -> Unit,
) {
    val saveValue = state.translations.firstOrNull()
        ?: state.machineTranslation
        ?: state.definitions.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SHEET_H_PADDING, vertical = SHEET_V_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Headword(word = state.word, canSpeak = canSpeak, onSpeak = onSpeak)

        if (state.ipa != null || state.partOfSpeech != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.ipa?.let { Chip(it) }
                state.partOfSpeech?.let { Chip(it, accent = true) }
            }
        }

        when {
            state.translations.isNotEmpty() ->
                TranslationBlock(state.translations.joinToString(", "))

            state.translationPending ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AuroraAccent)
                    Text("Translating…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

            state.machineTranslation != null ->
                TranslationBlock(state.machineTranslation)
        }

        if (state.definitions.isNotEmpty()) {
            var expanded by remember(state.word) { mutableStateOf(false) }
            val shown = if (expanded) state.definitions else state.definitions.take(COLLAPSED_DEFINITIONS)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("DEFINITIONS")
                shown.forEachIndexed { index, definition ->
                    Text(
                        text = "${index + 1}.  $definition",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                val remaining = state.definitions.size - COLLAPSED_DEFINITIONS
                if (remaining > 0) {
                    Text(
                        text = if (expanded) "Show less" else "Show $remaining more",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AuroraAccent,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
                    )
                }
            }
        }

        if (showSave && saveValue != null) {
            AuroraButton(text = "Save", onClick = { onSave(state.word, saveValue) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MachineContent(
    state: WordLookupState.Machine,
    onSave: (term: String, translation: String) -> Unit,
    showSave: Boolean,
    canSpeak: Boolean,
    onSpeak: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SHEET_H_PADDING, vertical = SHEET_V_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Headword(word = state.word, canSpeak = canSpeak, onSpeak = onSpeak)
        TranslationBlock(state.translation)
        if (showSave) {
            AuroraButton(text = "Save", onClick = { onSave(state.word, state.translation) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Headword(word: String, canSpeak: Boolean, onSpeak: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = word,
            fontFamily = Literata,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (canSpeak) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AuroraAccent.copy(alpha = 0.16f))
                    .clickable { onSpeak(word) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Pronounce", tint = AuroraAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun TranslationBlock(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("TRANSLATION")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AuroraAccent.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AuroraAccent,
            )
        }
    }
}

@Composable
private fun Chip(text: String, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (accent) AuroraAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, LexHairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ErrorContent(state: WordLookupState.Error) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = SHEET_H_PADDING, vertical = SHEET_V_PADDING)) {
        Text(text = state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AuroraAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
    )
}
