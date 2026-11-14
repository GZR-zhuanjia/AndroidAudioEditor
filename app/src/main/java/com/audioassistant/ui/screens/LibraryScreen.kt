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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import com.audioassistant.ui.ResultCategory
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.FeatureTone
import com.audioassistant.ui.components.IconBadge
import com.audioassistant.ui.components.NoteRow
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.SectionHeader
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun LibraryScreen(results: List<ResultItemUi>, category: ResultCategory, onCategory: (ResultCategory) -> Unit, onAction: (String, String) -> Unit) {
    val filtered = results.filter { category == ResultCategory.ALL || it.category == category }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            ResultCategory.entries.forEach { value ->
                FilterChip(selected = category == value, onClick = { onCategory(value) }, label = { Text(value.label) },
                    leadingIcon = { AppGlyph(when (value) {
                        ResultCategory.ALL -> AppIcon.LIBRARY
                        ResultCategory.VIDEO -> AppIcon.VIDEO
                        ResultCategory.MERGE -> AppIcon.CUT
                    }, modifier = Modifier.size(AppSpacing.large)) })
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            if (filtered.isEmpty()) item {
                EmptyState(if (category == ResultCategory.ALL) "还没有音频成果" else "暂无${category.label}",
                    "转换或导出成功后，音频会保存在这里。", icon = AppIcon.LIBRARY)
                NoteRow("普通导入歌曲不会加入成果库")
            } else item { SectionHeader("已保存音频", "${filtered.size} 个文件", AppIcon.AUDIO) }
            items(filtered, key = { it.id }) { result -> ResultCard(result, onAction) }
        }
    }
}

@Composable
private fun ResultCard(result: ResultItemUi, onAction: (String, String) -> Unit) {
    var expanded by rememberSaveable(result.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcon.AUDIO, FeatureTone.PURPLE, AppSpacing.touch)
                Column(Modifier.weight(1f)) {
                    Text(result.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${formatTime(result.durationUs)} · MP3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.category.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextAction("试听", AppIcon.PLAY, { onAction(result.id, "试听成果") })
            }
        }
    }
}
