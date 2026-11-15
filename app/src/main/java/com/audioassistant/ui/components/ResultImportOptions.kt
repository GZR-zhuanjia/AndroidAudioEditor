package com.audioassistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.theme.AppSpacing

/** Select an existing real result in-place; the workspace owns validation, references and autosave. */
@Composable
fun ResultImportOptions(results: List<ResultItemUi>, enabled: Boolean, onPick: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader("从成果库添加", AppIcon.LIBRARY, onClose)
        if (results.isEmpty()) {
            EmptyState("成果库还没有音频", "视频转换或拼接导出的音频会显示在这里。", icon = AppIcon.AUDIO)
        } else {
            Text("点击音频，直接加入当前剪辑", Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(results, key = { it.id }) { result ->
                    val available = result.error == null && !result.inProject
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled && available, role = Role.Button,
                            onClickLabel = "添加到当前剪辑") { onPick(result.id) },
                        headlineContent = { Text(result.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) },
                        supportingContent = {
                            Text(result.error ?: "${formatTime(result.durationUs)} · MP3 · ${formatSize(result.sizeBytes)}",
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { AppGlyph(AppIcon.AUDIO, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            Text(when { result.error != null -> "不可用"; result.inProject -> "已添加"; else -> "添加" },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (enabled && available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )
                }
            }
        }
    }
}
