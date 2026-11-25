package com.audioassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.WorkspaceUiState
import com.audioassistant.ui.theme.AppSpacing
import com.audioassistant.playback.ResultPlaybackSummary

@Composable
fun ExportOptions(state: WorkspaceUiState, onName: (String) -> Unit, onNormalize: (Boolean) -> Unit, onClose: () -> Unit, onStart: () -> Unit) {
    val name = state.exportSettings.baseName
    Column {
        SheetHeader("导出拼接音频", AppIcon.DOWNLOAD, onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(AppSpacing.page),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
            TextField(
                value = name, onValueChange = onName, enabled = state.ready, label = { Text("文件名称") },
                shape = MaterialTheme.shapes.medium,
                singleLine = true, isError = name.isBlank(), modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(if (name.isBlank()) "请输入文件名" else "无需输入扩展名，同名时创建新副本") },
            )
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    Row(Modifier.padding(AppSpacing.large), verticalAlignment = Alignment.CenterVertically) {
                        Text("输出格式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("MP3", style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = AppSpacing.large), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().toggleable(value = state.exportSettings.normalizeLoudness, role = Role.Switch,
                        enabled = state.ready, onValueChange = onNormalize).padding(AppSpacing.large),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                            Text("响度均衡", style = MaterialTheme.typography.titleSmall)
                            Text("减小不同歌曲间的音量差异", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.exportSettings.normalizeLoudness, enabled = state.ready, onCheckedChange = null)
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    SummaryRow("拼接歌曲", "${state.songs.size} 首")
                    SummaryRow("预计时长", formatTime(state.songs.sumOf { it.editedDurationUs }))
                    SummaryRow("保存位置", "内部成果库")
                }
            }
            NoteRow("普通试听不代表响度均衡后的效果")
        }
        ActionDock {
            PrimaryActionButton("开始导出", null, onStart, Modifier.fillMaxWidth(),
                state.ready && !state.isWorking && name.isNotBlank() && state.songs.isNotEmpty() && state.songs.all { it.error == null && it.editedDurationUs > 0 })
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun VideoNameOptions(state: WorkspaceUiState, onName: (String, String) -> Unit, onClose: () -> Unit, onStart: () -> Unit) {
    val videos = state.videos.filter { it.status != "已完成" }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 仅批量列表设高度上限；单文件由一个输入框的实际高度决定，不要求展开到屏幕比例。
        val batchMaxHeight = (maxHeight * 0.55f).coerceAtMost(360.dp)
        Column {
            SheetHeader("转换设置与命名", AppIcon.VIDEO, onClose)
            Text("MP3 · 自动保存到成果库", Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (videos.size <= 1) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page, vertical = AppSpacing.large)) {
                    val video = videos.singleOrNull()
                    if (video == null) Text("请先添加需要转换的视频。", style = MaterialTheme.typography.bodyMedium)
                    else ConversionNameField(state.videoNames[video.id].orEmpty(), { onName(video.id, it) })
                }
            } else {
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = batchMaxHeight),
                    contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            Text("${index + 1}. ${video.name}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ConversionNameField(state.videoNames[video.id].orEmpty(), { onName(video.id, it) })
                        }
                    }
                }
            }
            ActionDock {
                PrimaryActionButton("开始转换", null, onStart, Modifier.fillMaxWidth(),
                    state.ready && !state.isWorking && videos.isNotEmpty() && videos.all { !state.videoNames[it.id].isNullOrBlank() })
            }
        }
    }
}

@Composable
private fun ConversionNameField(name: String, onName: (String) -> Unit) {
    val focus = LocalFocusManager.current
    TextField(
        value = name, onValueChange = onName, label = { Text("文件名（不含扩展名）") },
        shape = MaterialTheme.shapes.medium, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = name.isBlank(),
        supportingText = if (name.isBlank()) { { Text("请输入文件名") } } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
    )
}

/** 仍仅供真实成果事件调用，不创建成功状态或模拟文件。 */
@Composable
fun SuccessOptions(result: ResultItemUi, onAction: (String) -> Unit, onLibrary: () -> Unit, onClose: () -> Unit,
    playback: ResultPlaybackSummary = ResultPlaybackSummary(), enabled: Boolean = true) {
    Column {
        SheetHeader("音频已保存", AppIcon.CHECK, onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(AppSpacing.page), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text(result.name, style = MaterialTheme.typography.titleMedium)
            NoteRow("关闭此面板不会删除内部成果", AppIcon.CHECK)
            PrimaryActionButton(if (playback.id == result.id && playback.playing) "暂停试听" else "试听",
                if (playback.id == result.id && playback.playing) AppIcon.PAUSE else AppIcon.PLAY,
                { onAction("试听成果") }, Modifier.fillMaxWidth(), enabled && !(playback.id == result.id && playback.loading))
            if (playback.id == result.id && playback.error != null) NoteRow(playback.error, AppIcon.ERROR)
            SecondaryActionButton("另存为…", AppIcon.DOWNLOAD, { onAction("另存") }, Modifier.fillMaxWidth(), enabled)
            TextAction("查看成果库", AppIcon.LIBRARY, onLibrary, Modifier.fillMaxWidth())
        }
    }
}
