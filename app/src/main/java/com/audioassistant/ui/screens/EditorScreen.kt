package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.SongItemUi
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.formatTime

/** 无播放器/编辑引擎时禁用操作，不生成波形、位置或剪切结果。 */
@Composable
fun EditorScreen(song: SongItemUi?) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(song?.name ?: "尚未选择歌曲", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (song == null) "请先从歌曲列表选择歌曲。"
                    else song.error ?: "试听和剪切暂未开放，原始文件没有改动。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Card(Modifier.fillMaxWidth().heightIn(min = 100.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("波形尚未生成", style = MaterialTheme.typography.titleMedium)
                        Text("添加歌曲并完成分析后，才会显示真实波形。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前位置 —")
                    Text("总时长 ${song?.let { formatTime(it.editedDurationUs) } ?: "—"}")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("播放 / 暂停") }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("后退 5 秒") }
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("前进 5 秒") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("标记开始") }
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("标记结束") }
                    }
                    Text("听到不要的部分时标记开始，标记结束后会暂停。", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("当前选区", style = MaterialTheme.typography.titleMedium)
                        Text("尚未标记 · 起点 — · 终点 — · 时长 —")
                        Row {
                            TextButton(onClick = {}, enabled = false) { Text("试听选区") }
                            TextButton(onClick = {}, enabled = false) { Text("取消标记") }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("试听与剪切功能暂未开放", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("删除这段") }
        }
    }
}
