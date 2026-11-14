package com.audioassistant.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.ResultCategory
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.FeatureTone
import com.audioassistant.ui.components.IconBadge
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.PrimaryActionButton
import com.audioassistant.ui.components.SectionHeader
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun LibraryScreen(
    results: List<ResultItemUi>, category: ResultCategory, onCategory: (ResultCategory) -> Unit,
    onMakeAudio: () -> Unit, isLoading: Boolean = false, onAction: (String, String) -> Unit,
) {
    val filtered = results.filter { category == ResultCategory.ALL || it.category == category }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            ResultCategory.entries.forEach { value ->
                FilterChip(selected = category == value, onClick = { onCategory(value) }, label = { Text(value.label) },
                    shape = MaterialTheme.shapes.small,
                    border = if (category == value) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    leadingIcon = { AppGlyph(when (value) {
                        ResultCategory.ALL -> AppIcon.LIBRARY
                        ResultCategory.VIDEO -> AppIcon.VIDEO
                        ResultCategory.MERGE -> AppIcon.CUT
                    }, modifier = Modifier.size(AppSpacing.large)) })
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            if (filtered.isEmpty() && !isLoading) item {
                EmptyState(if (results.isEmpty()) "还没有音频成果" else "暂无${category.label}",
                    if (results.isEmpty()) "先转换视频或拼接歌曲，成果会保存在这里。" else "试试其他分类，查看已保存的音频。",
                    icon = AppIcon.AUDIO) {
                    if (results.isEmpty()) PrimaryActionButton("去制作音频", AppIcon.ADD, onMakeAudio)
                    else TextAction("查看全部成果", AppIcon.LIBRARY, { onCategory(ResultCategory.ALL) })
                }
            } else if (filtered.isNotEmpty()) item { SectionHeader("已保存音频", "${filtered.size} 个文件", AppIcon.AUDIO) }
            items(filtered, key = { it.id }) { result -> ResultCard(result, onAction) }
        }
    }
}

@Composable
private fun ResultCard(result: ResultItemUi, onAction: (String, String) -> Unit) {
    var expanded by rememberSaveable(result.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(start = AppSpacing.medium, end = AppSpacing.xSmall, top = AppSpacing.small, bottom = AppSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xSmall), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcon.AUDIO, FeatureTone.PURPLE, 40.dp)
            Column(Modifier.weight(1f).padding(start = AppSpacing.small)) {
                Text(result.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatTime(result.durationUs)} · MP3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextAction("试听", AppIcon.PLAY, { onAction(result.id, "试听成果") })
            Column {
                IconButton(onClick = { expanded = true }) { AppGlyph(AppIcon.MORE, "${result.name}的更多操作") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(
                        Triple("重命名", AppIcon.EDIT, "重命名"), Triple("分享", AppIcon.SHARE, "分享"),
                        Triple("另存 U 盘", AppIcon.USB, "另存"), Triple("添加到拼接", AppIcon.ADD, "添加到拼接"),
                        Triple("删除", AppIcon.DELETE, "删除"),
                    ).forEach { (label, icon, action) ->
                        DropdownMenuItem(text = { Text(label) }, leadingIcon = { AppGlyph(icon) },
                            onClick = { expanded = false; onAction(result.id, action) })
                    }
                }
            }
        }
    }
}
