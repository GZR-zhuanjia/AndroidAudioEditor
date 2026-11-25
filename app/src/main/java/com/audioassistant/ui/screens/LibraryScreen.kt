package com.audioassistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.ResultCategory
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.FeatureTone
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.PrimaryActionButton
import com.audioassistant.ui.components.SectionHeader
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.components.formatSize
import com.audioassistant.playback.ResultPlaybackSummary
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun LibraryScreen(
    results: List<ResultItemUi>, category: ResultCategory, onCategory: (ResultCategory) -> Unit,
    onMakeAudio: () -> Unit, isLoading: Boolean = false, onAction: (String, String) -> Unit,
    enabled: Boolean = true, playback: ResultPlaybackSummary = ResultPlaybackSummary(),
) {
    val filtered = results.filter { category == ResultCategory.ALL || it.category == category }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            ResultCategory.entries.forEach { value ->
                FilterChip(selected = category == value, onClick = { onCategory(value) }, label = { Text(value.label) },
                    shape = MaterialTheme.shapes.small,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding) {
            if (filtered.isEmpty() && !isLoading) item {
                EmptyState(if (results.isEmpty()) "还没有音频成果" else "暂无${category.label}",
                    if (results.isEmpty()) "先转换视频或拼接歌曲，成果会保存在这里。" else "试试其他分类，查看已保存的音频。",
                    icon = AppIcon.AUDIO, tone = FeatureTone.NEUTRAL) {
                    if (results.isEmpty()) PrimaryActionButton("去制作音频", AppIcon.ADD, onMakeAudio)
                    else TextAction("查看全部成果", AppIcon.LIBRARY, { onCategory(ResultCategory.ALL) })
                }
            } else if (filtered.isNotEmpty()) item {
                Column(Modifier.padding(bottom = AppSpacing.large)) { SectionHeader("已保存音频", "${filtered.size} 个文件") }
            }
            items(filtered, key = { it.id }) { result ->
                ResultCard(result, onAction, enabled, playback, result.id == filtered.first().id, result.id == filtered.last().id)
            }
        }
    }
}

@Composable
private fun ResultCard(result: ResultItemUi, onAction: (String, String) -> Unit, enabled: Boolean,
    playback: ResultPlaybackSummary, first: Boolean, last: Boolean) {
    var expanded by rememberSaveable(result.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(
        topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp,
    ), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.padding(start = AppSpacing.large, end = AppSpacing.xSmall, top = AppSpacing.medium, bottom = AppSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xSmall), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                    Text(result.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${formatTime(result.durationUs)} · MP3 · ${formatSize(result.sizeBytes)} · ${result.category.label}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    result.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                val active = playback.id == result.id
                IconButton(onClick = { onAction(result.id, "试听成果") },
                    enabled = enabled && result.error == null && !(active && playback.loading)) {
                    if (active && playback.loading) CircularProgressIndicator(
                        Modifier.size(20.dp).semantics { contentDescription = "正在准备试听：${result.name}" }, strokeWidth = 2.dp)
                    else AppGlyph(if (active && playback.playing) AppIcon.PAUSE else AppIcon.PLAY,
                        "${if (active && playback.playing) "暂停试听" else "试听"}：${result.name}")
                }
                Column {
                    IconButton(onClick = { expanded = true }, enabled = enabled) { AppGlyph(AppIcon.MORE, "${result.name}的更多操作") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(
                            Triple("重命名", AppIcon.EDIT, "重命名"), Triple("分享", AppIcon.SHARE, "分享"),
                            Triple("另存为…", AppIcon.DOWNLOAD, "另存"), Triple("添加到拼接", AppIcon.ADD, "添加到拼接"),
                            Triple("删除", AppIcon.DELETE, "删除"),
                        ).forEach { (label, icon, action) ->
                            DropdownMenuItem(text = { Text(label) }, leadingIcon = { AppGlyph(icon) },
                                enabled = enabled && (result.error == null || action in listOf("重命名", "删除")),
                                onClick = { expanded = false; onAction(result.id, action) })
                        }
                    }
                }
            }
            if (!last) HorizontalDivider(Modifier.padding(horizontal = AppSpacing.large), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
