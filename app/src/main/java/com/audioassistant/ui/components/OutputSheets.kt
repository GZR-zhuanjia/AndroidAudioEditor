package com.audioassistant.ui.components

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.WorkspaceUiState

@Composable
fun ExportOptions(state: WorkspaceUiState, onName: (String) -> Unit, onNormalize: (Boolean) -> Unit) {
    val name = state.exportSettings.baseName
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("导出拼接成果", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name, onValueChange = onName, enabled = state.ready, label = { Text("文件名（不含扩展名）") },
            singleLine = true, isError = name.isBlank(), modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(if (name.isBlank()) "请输入文件名" else "同名时会创建新副本，不覆盖已有成果") },
        )
        Text("输出格式：MP3", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = state.exportSettings.normalizeLoudness,
                role = Role.Switch,
                enabled = state.ready,
                onValueChange = onNormalize,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("响度均衡", style = MaterialTheme.typography.titleMedium)
                Text("减小不同歌曲间的主观响度差异。普通试听不代表均衡后的效果。", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = state.exportSettings.normalizeLoudness, enabled = state.ready, onCheckedChange = null)
        }
        Text("歌曲数量：${state.songs.size} 首")
        Text("预计时长：${formatTime(state.songs.sumOf { it.editedDurationUs })}")
        Text("导出功能暂未开放。设置会随最近项目自动保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("开始导出") }
    }
}

@Composable
fun VideoNameOptions(state: WorkspaceUiState, onName: (String, String) -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (state.videos.size > 1) "批量转换命名" else "转换命名", style = MaterialTheme.typography.titleLarge)
        Text("输出格式：MP3 · 文件名无需输入扩展名")
        // 队列名称独立滚动，开始按钮留在面板底部。
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.videos.isEmpty()) item { EmptyState("还没有视频", "添加视频后，可在同一面板分别修改每个文件名。") }
            items(state.videos, key = { it.id }) { video ->
                val name = state.videoNames[video.id].orEmpty()
                OutlinedTextField(value = name, onValueChange = { onName(video.id, it) },
                    label = { Text(video.name) }, supportingText = { Text("文件名（不含扩展名）") },
                    singleLine = true, isError = name.isBlank(), modifier = Modifier.fillMaxWidth())
            }
        }
        Text("转换功能暂未开放，不会生成成果记录。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("开始转换") }
    }
}

/** 只供未来已验证的真实成果事件调用。当前没有成功事件生产者。 */
@Composable
fun SuccessOptions(result: ResultItemUi, onAction: (String) -> Unit, onLibrary: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("音频已保存", style = MaterialTheme.typography.titleLarge)
        Text(result.name)
        Text("成果保存在应用内。关闭此面板不会删除文件。")
        Button(onClick = { onAction("试听成果") }, modifier = Modifier.fillMaxWidth()) { Text("试听") }
        TextButton(onClick = { onAction("另存到 U 盘") }) { Text("另存到 U 盘") }
        TextButton(onClick = onLibrary) { Text("查看成果库") }
    }
}
