package com.audioassistant.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.audioassistant.ui.SongItemUi
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.SongReorderState
import com.audioassistant.ui.components.formatTime
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@Composable
fun EditWorkspaceScreen(
    songs: List<SongItemUi>, enabled: Boolean, cancelDragSignal: Int, onImport: () -> Unit, onLibrary: () -> Unit,
    onEditor: (String?) -> Unit, onExport: () -> Unit, onOrder: (List<String>) -> Unit,
    onMove: (String, Int) -> Unit, onRemove: (String) -> Unit, onRelink: (String) -> Unit,
    onRefresh: () -> Unit, onDragState: (Boolean) -> Unit,
) {
    val list = rememberLazyListState()
    val reorder = remember(list) { SongReorderState(list) }
    val commit = rememberUpdatedState(onOrder)
    val dragState = rememberUpdatedState(onDragState)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val cardPadding = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(songs) { reorder.sync(songs) }
    DisposableEffect(Unit) { onDispose { reorder.cancel(); dragState.value(false) } }
    fun cancel() { reorder.cancel(); dragState.value(false) }
    LaunchedEffect(cancelDragSignal) { cancel() }
    LaunchedEffect(reorder.draggedId) {
        var lastFrame = 0L
        while (reorder.draggedId != null && coroutineContext.isActive) {
            val frame = withFrameNanos { it }
            val dt = if (lastFrame == 0L) 0f else ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            lastFrame = frame
            val layout = list.layoutInfo
            val speed = when {
                reorder.pointerY < layout.viewportStartOffset + edge -> -((layout.viewportStartOffset + edge - reorder.pointerY) / edge).coerceIn(0f, 1f)
                reorder.pointerY > layout.viewportEndOffset - edge -> ((reorder.pointerY - layout.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) { list.scrollBy(speed * edge * 10 * dt); reorder.moveToTarget() }
        }
    }
    val display = if (reorder.draggedId != null) reorder.order else songs
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "heading") {
                Text("按顺序，拼成你喜欢的声音", style = MaterialTheme.typography.titleLarge)
                Text("长按左侧拖动柄排序，松手保存；也可使用上移、下移。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "import") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImport, enabled = enabled && reorder.draggedId == null, modifier = Modifier.fillMaxWidth()) { Text("从文件添加歌曲") }
                    OutlinedButton(onClick = onLibrary, enabled = enabled && reorder.draggedId == null, modifier = Modifier.fillMaxWidth()) { Text("从成果库添加") }
                }
            }
            item(key = "count") {
                Text("歌曲顺序 · ${songs.size} 首", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { cancel() }, enabled = reorder.draggedId != null) {
                    Text(if (reorder.draggedId != null) "取消拖动，恢复原顺序" else "长按左柄，拖动排序")
                }
                if (songs.isNotEmpty()) TextButton(onClick = onRefresh, enabled = enabled) { Text("重新检查源文件") }
            }
            if (songs.isEmpty()) item(key = "empty") {
                EmptyState("还没有歌曲", "从系统文件或成果库添加歌曲。试听与剪切将在后续版本开放。")
                TextButton(onClick = { onEditor(null) }) { Text("查看裁剪器") }
            }
            itemsIndexed(display, key = { _, song -> "song:${song.id}" }) { index, song ->
                Card(Modifier.fillMaxWidth().zIndex(if (reorder.draggedId == song.id) 1f else 0f)
                    .graphicsLayer { translationY = reorder.translation(song.id) }) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = "长按拖动排序：${song.name}",
                            modifier = Modifier.size(48.dp).pointerInput(song.id, enabled) {
                                if (enabled) detectDragGesturesAfterLongPress(
                                    onDragStart = { offset -> if (reorder.begin(song.id, cardPadding + offset.y)) dragState.value(true) },
                                    onDragEnd = {
                                        if (reorder.draggedId != null) {
                                            val ids = reorder.finish()
                                            dragState.value(false)
                                            commit.value(ids)
                                        }
                                    },
                                    onDragCancel = { cancel() },
                                    onDrag = { change, delta -> change.consume(); reorder.drag(delta.y) },
                                )
                            })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${index + 1}. ${song.name}", style = MaterialTheme.typography.titleMedium)
                            Text("原始 ${formatTime(song.originalDurationUs)} · 剪后 ${formatTime(song.editedDurationUs)}")
                            song.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row {
                                TextButton(onClick = { onEditor(song.id) }, enabled = enabled && reorder.draggedId == null && song.error == null) { Text("裁剪") }
                                TextButton(onClick = { onRemove(song.id) }, enabled = enabled && reorder.draggedId == null) { Text("移除") }
                            }
                            if (song.error != null) TextButton(onClick = { onRelink(song.id) }, enabled = enabled && reorder.draggedId == null) { Text("重新选择同一原曲") }
                            Row {
                                TextButton(onClick = { onMove(song.id, -1) }, enabled = enabled && reorder.draggedId == null && index > 0) { Text("上移") }
                                TextButton(onClick = { onMove(song.id, 1) }, enabled = enabled && reorder.draggedId == null && index < display.lastIndex) { Text("下移") }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${songs.size} 首 · 预计 ${formatTime(songs.sumOf { it.editedDurationUs })}")
            Button(onClick = onExport, enabled = enabled && reorder.draggedId == null, modifier = Modifier.fillMaxWidth()) { Text("导出设置") }
        }
    }
}
