package com.audioassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audioassistant.data.SaveLocation
import com.audioassistant.playback.ResultPreview
import com.audioassistant.ui.FileOperationUi
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.theme.AppSpacing
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ResultPlayerDock(preview: ResultPreview) {
    val state by preview.state.collectAsStateWithLifecycle()
    val id = state.id ?: return
    var dragging by remember(id) { mutableStateOf<Float?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.name.ifBlank { "正在准备成果试听" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                TextAction(if (state.error != null) "重试" else if (state.playing) "暂停" else "播放",
                    if (state.playing) AppIcon.PAUSE else AppIcon.PLAY, { preview.toggle(id) }, enabled = !state.loading)
                IconButton(onClick = { preview.stop() }) { AppGlyph(AppIcon.CLOSE, "停止成果试听") }
            }
            state.error?.let { NoteRow(it, AppIcon.ERROR) }
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else if (state.error == null) {
                val fraction = if (state.durationUs > 0) (state.positionUs.toDouble() / state.durationUs).toFloat().coerceIn(0f, 1f) else 0f
                Slider(value = dragging ?: fraction, onValueChange = { dragging = it }, enabled = state.ready,
                    onValueChangeFinished = { dragging?.let(preview::seek); dragging = null }, modifier = Modifier.fillMaxWidth())
                Text("${formatTime(state.positionUs)} / ${formatTime(state.durationUs)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FileOperationDock(flow: StateFlow<FileOperationUi?>, onCancel: () -> Unit) {
    val state by flow.collectAsStateWithLifecycle()
    val task = state ?: return
    ActionDock {
        Text(task.label, style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { task.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        TextAction(if (task.cancelling) "正在取消…" else "取消", AppIcon.CLOSE, onCancel, enabled = !task.cancelling)
    }
}

@Composable
fun RenameResultDialog(result: ResultItemUi, enabled: Boolean, onDismiss: () -> Unit, error: String?, onRename: (String) -> Unit) {
    var name by rememberSaveable(result.id) { mutableStateOf(result.name.replace(Regex("(?i)\\.mp3$"), "")) }
    AlertDialog(onDismissRequest = onDismiss, icon = { AppGlyph(AppIcon.EDIT) }, title = { Text("重命名音频") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = name, onValueChange = { name = it }, enabled = enabled, label = { Text("文件名（不含扩展名）") },
                singleLine = true, isError = name.isBlank(), supportingText = { Text("同名时创建新名称，剪辑引用不受影响") })
            error?.let { NoteRow(it, AppIcon.ERROR) }
        } },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = enabled && name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun DeleteResultDialog(result: ResultItemUi, enabled: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { AppGlyph(AppIcon.DELETE) }, title = { Text("删除成果") },
        text = { Text("${result.name}\n\n" + (if (result.inProject) "该音频仍被最近剪辑引用，只移除成果库记录；项目释放后再清理内部文件。已另存的副本不受影响。"
            else "将移除成果库记录并清理无引用内部文件。已分享或另存到外部的副本不受影响。"), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDelete, enabled = enabled) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun SaveCopyOptions(result: ResultItemUi, location: SaveLocation?, enabled: Boolean, error: String?, onSave: () -> Unit, onChoose: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader("另存音频副本", AppIcon.USB, onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(AppSpacing.page), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Text(result.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            NoteRow("选择U盘中的文件夹即可另存；内部成果会保留，同名会新建副本。", AppIcon.USB)
            error?.let { NoteRow(it, AppIcon.ERROR) }
            if (location != null) {
                Text("上次位置：${location.name}", style = MaterialTheme.typography.bodyMedium)
                if (!location.persistent) NoteRow("此位置未保留长期读写授权，重开后可能需要重新选择")
            }
        }
        ActionDock {
            if (location != null) {
                PrimaryActionButton("保存到此位置", AppIcon.USB, onSave, Modifier.fillMaxWidth(), enabled)
                SecondaryActionButton("重新选择位置", AppIcon.FOLDER, onChoose, Modifier.fillMaxWidth(), enabled)
            } else PrimaryActionButton("选择保存位置", AppIcon.FOLDER, onChoose, Modifier.fillMaxWidth(), enabled)
        }
    }
}
