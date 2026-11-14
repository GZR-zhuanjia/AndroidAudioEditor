package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.VideoItemUi
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.formatSize
import com.audioassistant.ui.components.formatTime

@Composable
fun VideoScreen(
    videos: List<VideoItemUi>, enabled: Boolean, onPhoto: () -> Unit, onFiles: () -> Unit,
    onSettings: () -> Unit, onRemove: (String) -> Unit, onUnavailable: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("把视频中的声音留下来", style = MaterialTheme.typography.titleLarge)
                Text("可以连续添加多个视频，统一转换为 MP3。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPhoto, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("从相册选择") }
                    OutlinedButton(onClick = onFiles, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("从文件中选择") }
                }
            }
            item { Text("待转换 · ${videos.size} 个", style = MaterialTheme.typography.titleMedium) }
            if (videos.isEmpty()) {
                item { EmptyState("还没有添加视频", "添加后可在这里查看时长、文件大小和转换状态。转换功能尚未开放。") }
            }
            items(videos, key = { it.id }) { video ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(video.name, style = MaterialTheme.typography.titleMedium)
                        Text("${formatTime(video.durationUs)} · ${formatSize(video.sizeBytes)}")
                        Text(video.status)
                        video.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
                        video.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row {
                            TextButton(onClick = { onRemove(video.id) }, enabled = enabled) { Text("移除") }
                            if (video.error != null) TextButton(onClick = { onUnavailable("重试转换") }) { Text("重试") }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("默认 MP3 · 转换功能尚未开放", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("转换设置与命名") }
        }
    }
}
