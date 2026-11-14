package com.audioassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.WorkspaceUiState
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun ExportOptions(state: WorkspaceUiState, onName: (String) -> Unit, onNormalize: (Boolean) -> Unit, onClose: () -> Unit) {
    val name = state.exportSettings.baseName
    Column {
        SheetHeader("导出拼接音频", AppIcon.DOWNLOAD, onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(AppSpacing.page),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
            OutlinedTextField(
                value = name, onValueChange = onName, enabled = state.ready, label = { Text("文件名称") },
                leadingIcon = { AppGlyph(AppIcon.EDIT) }, shape = MaterialTheme.shapes.medium,
                singleLine = true, isError = name.isBlank(), modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(if (name.isBlank()) "请输入文件名" else "无需输入扩展名，同名时创建新副本") },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                AppGlyph(AppIcon.AUDIO, tint = MaterialTheme.colorScheme.primary)
                Text("输出格式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text("MP3", Modifier.padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small), style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(Modifier.fillMaxWidth().toggleable(value = state.exportSettings.normalizeLoudness, role = Role.Switch,
                enabled = state.ready, onValueChange = onNormalize),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                AppGlyph(AppIcon.TUNE, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("响度均衡", style = MaterialTheme.typography.titleSmall)
                    Text("减小不同歌曲间的音量差异", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.exportSettings.normalizeLoudness, enabled = state.ready, onCheckedChange = null)
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    SummaryRow("拼接歌曲", "${state.songs.size} 首", AppIcon.AUDIO)
                    SummaryRow("预计时长", formatTime(state.songs.sumOf { it.editedDurationUs }), AppIcon.HISTORY)
                    SummaryRow("保存位置", "内部成果库", AppIcon.LIBRARY)
                }
            }
            NoteRow("普通试听不代表响度均衡后的效果")
        }
        ActionDock {
            NoteRow("导出暂未开放 · 设置自动保存")
            PrimaryActionButton("开始导出", AppIcon.DOWNLOAD, {}, Modifier.fillMaxWidth(), false)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, icon: AppIcon) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        AppGlyph(icon, modifier = Modifier.size(AppSpacing.large), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun VideoNameOptions(state: WorkspaceUiState, onName: (String, String) -> Unit, onClose: () -> Unit) {
    Column {
        SheetHeader(if (state.videos.size > 1) "批量转换命名" else "转换设置与命名", AppIcon.VIDEO, onClose)
        Column(Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.small)) { NoteRow("输出 MP3 · 文件名无需输入扩展名", AppIcon.AUDIO) }
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            if (state.videos.isEmpty()) item { EmptyState("还没有视频", "请先添加需要转换的视频。", icon = AppIcon.VIDEO, tone = FeatureTone.BLUE) }
            items(state.videos, key = { it.id }) { video ->
                val name = state.videoNames[video.id].orEmpty()
                OutlinedTextField(value = name, onValueChange = { onName(video.id, it) }, label = { Text(video.name) },
                    leadingIcon = { AppGlyph(AppIcon.EDIT) }, supportingText = { Text("文件名（不含扩展名）") },
                    shape = MaterialTheme.shapes.medium, singleLine = true, isError = name.isBlank(), modifier = Modifier.fillMaxWidth())
            }
        }
        ActionDock {
            NoteRow("转换功能暂未开放，不会生成成果记录")
            PrimaryActionButton("开始转换", AppIcon.VIDEO, {}, Modifier.fillMaxWidth(), false)
        }
    }
}

/** 仍仅供真实成果事件调用，不创建成功状态或模拟文件。 */
@Composable
fun SuccessOptions(result: ResultItemUi, onAction: (String) -> Unit, onLibrary: () -> Unit, onClose: () -> Unit) {
    Column {
        SheetHeader("音频已保存", AppIcon.CHECK, onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(AppSpacing.page), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text(result.name, style = MaterialTheme.typography.titleMedium)
            NoteRow("关闭此面板不会删除内部成果", AppIcon.CHECK)
            PrimaryActionButton("试听", AppIcon.PLAY, { onAction("试听成果") }, Modifier.fillMaxWidth())
            SecondaryActionButton("另存到 U 盘", AppIcon.USB, { onAction("另存到 U 盘") }, Modifier.fillMaxWidth())
            TextAction("查看成果库", AppIcon.LIBRARY, onLibrary, Modifier.fillMaxWidth())
        }
    }
}
