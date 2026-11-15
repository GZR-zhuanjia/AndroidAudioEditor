package com.audioassistant.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.audioassistant.ui.SongItemUi
import com.audioassistant.ui.ProcessingUi
import com.audioassistant.ui.components.ProcessingStatus
import com.audioassistant.ui.components.ActionDock
import com.audioassistant.ui.components.ActionPair
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.NoteRow
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.PrimaryActionButton
import com.audioassistant.ui.components.SecondaryActionButton
import com.audioassistant.ui.components.SectionHeader
import com.audioassistant.ui.components.SongReorderState
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.theme.AppSpacing
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

@Composable
fun EditWorkspaceScreen(
    songs: List<SongItemUi>, enabled: Boolean, cancelDragSignal: Int, onImport: () -> Unit, onLibrary: () -> Unit,
    onEditor: (String?) -> Unit, onExport: () -> Unit, onOrder: (List<String>) -> Unit,
    onMove: (String, Int) -> Unit, onRemove: (String) -> Unit, onRelink: (String) -> Unit,
    onRefresh: () -> Unit, onDragState: (Boolean) -> Unit,
    preview: WorkspacePreviewUi, onPreviewAll: () -> Unit, processing: ProcessingUi?, onCancel: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val reorder = remember(list) { SongReorderState(list) }
    val commit = rememberUpdatedState(onOrder)
    val dragState = rememberUpdatedState(onDragState)
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val cardPadding = with(LocalDensity.current) { AppSpacing.medium.toPx() }
    val handleHeight = with(LocalDensity.current) { AppSpacing.touch.toPx() }
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
        if (songs.isNotEmpty()) Column(Modifier.padding(horizontal = AppSpacing.page), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            ActionPair(
                { SecondaryActionButton("添加歌曲", AppIcon.ADD, onImport, it, enabled && reorder.draggedId == null) },
                { SecondaryActionButton("从成果库添加", AppIcon.LIBRARY, onLibrary, it, enabled && reorder.draggedId == null) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("歌曲 · ${songs.size} 首", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (reorder.draggedId != null) TextAction("取消拖动", AppIcon.CLOSE, { cancel() })
                else TextAction("检查文件", AppIcon.HISTORY, onRefresh, enabled = enabled)
            }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            if (songs.isEmpty()) item(key = "empty") {
                EmptyState("尚未添加歌曲", "添加歌曲后，单独裁剪或按顺序拼接。", icon = AppIcon.AUDIO) {
                    PrimaryActionButton("添加歌曲", AppIcon.ADD, onImport, Modifier.fillMaxWidth(), enabled)
                    SecondaryActionButton("从成果库添加", AppIcon.LIBRARY, onLibrary, Modifier.fillMaxWidth(), enabled)
                }
                TextAction("查看裁剪器", AppIcon.CUT, { onEditor(null) })
            }
            itemsIndexed(display, key = { _, song -> "song:${song.id}" }) { index, song ->
                val selected = selectedId == song.id
                var headerHeight by remember { mutableFloatStateOf(handleHeight) }
                Card(onClick = { if (reorder.draggedId == null) selectedId = if (selected) null else song.id },
                    modifier = Modifier.fillMaxWidth().zIndex(if (reorder.draggedId == song.id) 1f else 0f)
                        .graphicsLayer { translationY = reorder.translation(song.id) },
                    shape = MaterialTheme.shapes.large,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(AppSpacing.medium)) {
                        Row(Modifier.onSizeChanged { headerHeight = it.height.toFloat() }, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(AppSpacing.touch).pointerInput(song.id, enabled) {
                                if (enabled) detectDragGesturesAfterLongPress(
                                    onDragStart = { offset -> if (reorder.begin(song.id, cardPadding + ((headerHeight - handleHeight) / 2f).coerceAtLeast(0f) + offset.y)) dragState.value(true) },
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
                            }) { AppGlyph(AppIcon.DRAG, "长按拖动排序：${song.name}") }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                                    Text(song.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("原始 ${formatTime(song.originalDurationUs)} · 剪后 ${formatTime(song.editedDurationUs)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextAction("裁剪", AppIcon.CUT, { onEditor(song.id) }, enabled = enabled && reorder.draggedId == null && song.error == null)
                            }
                        }
                        song.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextAction("重新选择同一原曲", AppIcon.FOLDER, { onRelink(song.id) }, enabled = enabled && reorder.draggedId == null)
                        }
                        if (selected) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextAction("上移", AppIcon.UP, { onMove(song.id, -1) }, enabled = enabled && reorder.draggedId == null && index > 0)
                                TextAction("下移", AppIcon.DOWN, { onMove(song.id, 1) }, enabled = enabled && reorder.draggedId == null && index < display.lastIndex)
                                TextAction("移除", AppIcon.DELETE, { onRemove(song.id) }, enabled = enabled && reorder.draggedId == null)
                            }
                        }
                    }
                }
            }
            if (songs.isNotEmpty()) item(key = "sorting_hint") { NoteRow("长按左侧拖动柄排序，点按卡片显示更多操作", AppIcon.DRAG) }
        }
        if (processing != null) ActionDock { ProcessingStatus(processing, onCancel) }
        else if (songs.isNotEmpty()) ActionDock {
            SectionHeader("${songs.size} 首歌曲", "预计 ${formatTime(songs.sumOf { it.editedDurationUs })}")
            ActionPair(
                first = { SecondaryActionButton(if (preview.playing) "暂停试听" else "试听全部",
                    if (preview.playing) AppIcon.PAUSE else AppIcon.PLAY, onPreviewAll, it,
                    enabled && !preview.busy && songs.any { song -> song.editedDurationUs > 0 } && reorder.draggedId == null) },
                second = { PrimaryActionButton("导出音频", AppIcon.DOWNLOAD, onExport, it, enabled && reorder.draggedId == null) },
                stackAt = 240.dp, stackForLargeText = false,
            )
            preview.error?.let { NoteRow(it, AppIcon.ERROR) }
            NoteRow("试听未做响度均衡 · 可在导出时开启")
        }
    }
}
