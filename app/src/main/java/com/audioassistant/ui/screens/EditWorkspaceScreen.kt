package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.SongItemUi
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.formatTime

@Composable
fun EditWorkspaceScreen(
    songs: List<SongItemUi>, onImport: () -> Unit, onLibrary: () -> Unit,
    onEditor: (String?) -> Unit, onExport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("按顺序，拼成你喜欢的声音", style = MaterialTheme.typography.titleLarge)
                Text("每首歌可以单独裁剪，原始文件始终保留。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("从文件添加歌曲") }
                    OutlinedButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) { Text("从成果库添加") }
                }
            }
            item { Text("歌曲顺序 · ${songs.size} 首", style = MaterialTheme.typography.titleMedium) }
            if (songs.isEmpty()) {
                item {
                    EmptyState("还没有歌曲", "导入后，歌曲会依次排列在这里。当前版本暂未开放文件导入与编辑。")
                    TextButton(onClick = { onEditor(null) }) { Text("查看裁剪器") }
                }
            }
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 阶段 3 才绑定长按拖动；当前不误导为已能拖动。
                        Icon(Icons.Default.Menu, contentDescription = "排序柄，排序暂未开放")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${index + 1}. ${song.name}", style = MaterialTheme.typography.titleMedium)
                            Text("原始 ${formatTime(song.originalDurationUs)} · 剪后 ${formatTime(song.editedDurationUs)}")
                            TextButton(onClick = { onEditor(song.id) }) { Text("裁剪") }
                            Row {
                                TextButton(onClick = {}, enabled = false) { Text("上移") }
                                TextButton(onClick = {}, enabled = false) { Text("下移") }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${songs.size} 首 · 预计 ${formatTime(songs.sumOf { it.editedDurationUs })}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("导出设置") }
        }
    }
}
