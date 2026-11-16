package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.audioassistant.ui.SongItemUi
import com.audioassistant.ui.components.ActionPair
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.NoteRow
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.PrimaryActionButton
import com.audioassistant.ui.components.SecondaryActionButton
import com.audioassistant.ui.components.SectionHeader
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.components.WaveformPanel
import com.audioassistant.ui.theme.AppSpacing
import com.audioassistant.waveform.WaveformState
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun EditorScreen(
    song: SongItemUi?, onBack: () -> Unit, onAdjustTime: () -> Unit,
    onAction: (EditorAction) -> Unit, presentation: EditorPresentation = EditorPresentation(), isLoading: Boolean = false,
    waveform: WaveformState = WaveformState(), onWaveformRetry: () -> Unit = {},
    onWaveformCancel: () -> Unit = {}, onWaveformSeek: (Long) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekCancel: () -> Unit = {},
) {
    // 项目初始化由外层真实加载横幅反馈，不提前宣称没有歌曲。
    if (isLoading) return
    // 无歌曲不构建工作台；失效来源也不展示一套无效的编辑控件。
    if (song == null || song.error != null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PagePadding) {
            item {
                EmptyState(
                    if (song == null) "尚未选择歌曲" else "歌曲暂不可用",
                    if (song == null) "从歌曲列表选择一首，开始裁剪。" else requireNotNull(song.error),
                    icon = if (song == null) AppIcon.AUDIO else AppIcon.ERROR,
                ) {
                    PrimaryActionButton("返回歌曲列表", AppIcon.BACK, onBack)
                }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxHeight < 440.dp) {
            Row(Modifier.fillMaxSize()) {
                EditorPlaybackPanel(song, presentation, onAction, onWaveformSeek, onSeekStart, onSeekCancel,
                    Modifier.weight(1f).verticalScroll(rememberScrollState()))
                EditorInformation(presentation, onAdjustTime, onAction, waveform, onWaveformRetry, onWaveformCancel, onWaveformSeek, onSeekStart, onSeekCancel, Modifier.weight(1f))
            }
        } else {
            Column {
                // 主控固定在上部时间/进度旁，下面的波形与选区独立滚动；小屏只约束主控分区。
                EditorPlaybackPanel(song, presentation, onAction, onWaveformSeek, onSeekStart, onSeekCancel,
                    Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.65f).verticalScroll(rememberScrollState()))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EditorInformation(presentation, onAdjustTime, onAction, waveform, onWaveformRetry, onWaveformCancel, onWaveformSeek, onSeekStart, onSeekCancel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EditorPlaybackPanel(
    song: SongItemUi, state: EditorPresentation, onAction: (EditorAction) -> Unit,
    onSeek: (Long) -> Unit, onSeekStart: () -> Unit, onSeekCancel: () -> Unit, modifier: Modifier,
) {
    var pending by remember(state.songId, state.originalPreview, state.durationUs) { mutableStateOf<Float?>(null) }
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is DragInteraction.Cancel && pending != null) { pending = null; onSeekCancel() } }
    }
    DisposableEffect(state.songId, state.originalPreview, state.durationUs) { onDispose { onSeekCancel() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { pending = null; onSeekCancel() }
    val total = state.durationUs.coerceAtLeast(0)
    val fraction = if (total > 0) ((state.positionUs ?: 0).toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    val displayUs = pending?.let { (it.toDouble() * total).roundToLong() } ?: state.positionUs
    Column(modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
        Text(song.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("原始 ${formatTime(song.originalDurationUs)} · 已保留 ${formatTime(song.editedDurationUs)} · ${state.previewLabel}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (pending == null) "当前播放时间" else "定位时间", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(editorTime(displayUs), style = MaterialTheme.typography.headlineSmall)
            }
            Text("/ ${formatTime(total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = pending ?: fraction, interactionSource = interactions,
            enabled = state.editingReady && state.playbackReady && total > 0 && (!state.seeking || pending != null),
            onValueChange = { value -> val first = pending == null; pending = value; if (first) onSeekStart() },
            onValueChangeFinished = {
                pending?.let { onSeek((it.toDouble() * total).roundToLong().coerceIn(0, total)) }
                pending = null
            }, modifier = Modifier.fillMaxWidth())
        EditorControls(state, onAction, scrubbing = pending != null)
    }
}

@Composable
private fun EditorInformation(
    state: EditorPresentation, onAdjustTime: () -> Unit,
    onAction: (EditorAction) -> Unit, waveform: WaveformState, onWaveformRetry: () -> Unit,
    onWaveformCancel: () -> Unit, onWaveformSeek: (Long) -> Unit, onSeekStart: () -> Unit, onSeekCancel: () -> Unit, modifier: Modifier,
) {
    val editingEnabled = state.editingReady && !state.seeking
    val playbackEnabled = state.playbackReady && !state.seeking
    LazyColumn(modifier, contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        state.error?.let { message -> item { NoteRow(message, AppIcon.ERROR) } }
        item {
            when (val stage = state.stage) {
                EditorStage.Ready -> {
                    if (state.busy) NoteRow("正在准备试听或保存编辑…")
                    else if (state.editedDurationUs == 0L && state.editingReady) NoteRow("已无保留片段，可从更多编辑中撤销或恢复")
                    else if (!state.playbackReady && state.error == null) NoteRow("正在准备音频…")
                    else if (state.playbackReady) NoteRow(if (state.originalPreview) "原曲试听中，可从更多编辑切回编辑结果" else "可以先标记开始或结束，再定位另一端", AppIcon.START)
                }
                is EditorStage.StartMarked -> StagePanel {
                    SectionHeader("已标记开始", icon = AppIcon.START)
                    Text(editorTime(stage.startUs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("继续播放或拖动进度条，定位另一端后标记结束。", style = MaterialTheme.typography.bodyMedium)
                    TextAction("取消标记", AppIcon.CLOSE, { onAction(EditorAction.CANCEL_MARK) }, enabled = editingEnabled)
                }
                is EditorStage.EndMarked -> StagePanel {
                    SectionHeader("已标记结束", icon = AppIcon.END)
                    Text(editorTime(stage.endUs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("可拖回前面定位，再标记开始。", style = MaterialTheme.typography.bodyMedium)
                    TextAction("取消标记", AppIcon.CLOSE, { onAction(EditorAction.CANCEL_MARK) }, enabled = editingEnabled)
                }
                is EditorStage.RangeMarked -> StagePanel {
                    SectionHeader("已选中片段", icon = AppIcon.CUT)
                    Text("${editorTime(stage.startUs)} — ${editorTime(stage.endUs)}", style = MaterialTheme.typography.titleSmall)
                    Text("选区时长 ${editorTime((stage.endUs - stage.startUs).coerceAtLeast(0))}", style = MaterialTheme.typography.bodySmall)
                    ActionPair(
                        { SecondaryActionButton("试听选区", AppIcon.PLAY, { onAction(EditorAction.PREVIEW_RANGE) }, it, playbackEnabled) },
                        { SecondaryActionButton("调整时间", AppIcon.TUNE, onAdjustTime, it, editingEnabled) },
                    )
                    PrimaryActionButton("删除这段", AppIcon.DELETE, { onAction(EditorAction.DELETE_RANGE) }, Modifier.fillMaxWidth(), editingEnabled)
                    TextAction("取消标记", AppIcon.CLOSE, { onAction(EditorAction.CANCEL_MARK) }, enabled = editingEnabled)
                }
                is EditorStage.AtSeam -> StagePanel {
                    SectionHeader(stage.message, icon = AppIcon.CHECK)
                    Text("${if (state.isPlaying) "接缝位置" else "已暂停在接缝"} ${editorTime(stage.positionUs)}", style = MaterialTheme.typography.bodyMedium)
                    ActionPair(
                        { SecondaryActionButton("试听接缝", AppIcon.SPLIT, { onAction(EditorAction.PREVIEW_SEAM) }, it, playbackEnabled) },
                        { SecondaryActionButton("撤销", AppIcon.UNDO, { onAction(EditorAction.UNDO) }, it, editingEnabled && state.canUndo) },
                    )
                    PrimaryActionButton("继续播放", AppIcon.PLAY, { onAction(EditorAction.RESUME) }, Modifier.fillMaxWidth(), playbackEnabled)
                }
            }
        }
        item {
            WaveformPanel(waveform, state, onWaveformSeek, onWaveformRetry, onWaveformCancel, onSeekStart, onSeekCancel)
        }
        item { NoteRow("普通试听未做响度均衡") }
    }
}

@Composable
private fun StagePanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small), content = content)
    }
}

@Composable
private fun EditorControls(state: EditorPresentation, onAction: (EditorAction) -> Unit, scrubbing: Boolean) {
    val ready = state.playbackReady && !scrubbing && !state.seeking
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        ActionPair(
            first = { SecondaryActionButton("标记开始", AppIcon.START, { onAction(EditorAction.MARK_START) }, it, state.editingReady && ready && state.canMark) },
            second = { SecondaryActionButton("标记结束", AppIcon.END, { onAction(EditorAction.MARK_END) }, it, state.editingReady && ready && state.canMark) },
            stackAt = 240.dp, stackForLargeText = false,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            SeekControl("后退 5 秒", AppIcon.BACKWARD, { onAction(EditorAction.BACKWARD) }, ready, Modifier.weight(1f))
            PrimaryActionButton(if (state.isPlaying) "暂停" else "播放", if (state.isPlaying) AppIcon.PAUSE else AppIcon.PLAY,
                { onAction(EditorAction.PLAY_PAUSE) }, Modifier.weight(1f).heightIn(min = 56.dp), ready)
            SeekControl("前进 5 秒", AppIcon.FORWARD, { onAction(EditorAction.FORWARD) }, ready, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SeekControl(label: String, icon: AppIcon, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
            AppGlyph(icon, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun editorTime(timeUs: Long?): String {
    if (timeUs == null) return "—"
    val milliseconds = timeUs.coerceAtLeast(0) / 1000
    return String.format(Locale.CHINA, "%02d:%02d.%03d", milliseconds / 60_000, milliseconds / 1000 % 60, milliseconds % 1000)
}
