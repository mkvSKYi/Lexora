package com.reader.feature.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.LexHairline
import com.reader.core.designsystem.theme.LexSurfaceHigh
import com.reader.core.designsystem.theme.Literata

private val PopoverShape = RoundedCornerShape(20.dp)

@Composable
fun TranslationPopover(
    state: TranslationPopupState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(min = 180.dp, max = 320.dp)
            .border(1.dp, LexHairline, PopoverShape),
        shape = PopoverShape,
        shadowElevation = 10.dp,
        color = LexSurfaceHigh,
    ) {
        when (state) {
            TranslationPopupState.Loading -> LoadingContent()
            is TranslationPopupState.Result -> ResultContent(state, onSave)
            is TranslationPopupState.Error -> ErrorContent(state)
        }
    }
}

@Composable
private fun LoadingContent() {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AuroraAccent)
        Text(
            text = "Translating…",
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultContent(state: TranslationPopupState.Result, onSave: () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Translate, contentDescription = null, tint = AuroraAccent, modifier = Modifier.size(15.dp))
            Text(
                text = state.source,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Text(
            text = state.translation,
            fontFamily = Literata,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AuroraAccent.copy(alpha = 0.16f))
                .clickable(onClick = onSave)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = AuroraAccent, modifier = Modifier.size(16.dp))
            Text("Save", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AuroraAccent)
        }
    }
}

@Composable
private fun ErrorContent(state: TranslationPopupState.Error) {
    Text(
        text = state.message,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}
