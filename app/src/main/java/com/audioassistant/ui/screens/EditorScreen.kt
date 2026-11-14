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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.SongItemUi
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
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.theme.AppSpacing
import java.util.Locale

@Composable
fun EditorScreen(
    song: SongItemUi?, onBack: () -> Unit, onAdjustTime: () -> Unit,
    onAction: (EditorAction) -> Unit, presentation: EditorPresentation = EditorPresentation(), isLoading: Boolean = false,
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
            Row {
                EditorInformation(song, presentation, onAdjustTime, onAction, Modifier.weight(1f))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { EditorControls(presentation, onAction) }
            }
        } else {
            Column {
                EditorInformation(song, presentation, onAdjustTime, onAction, Modifier.weight(1f))
                // 选区/接缝反馈仅改变信息区，主控区域不增减行、不因阶段跳动。
                EditorControls(presentation, onAction)
            }
        }
    }
}

@Composable
private fun EditorInformation(
    song: SongItemUi, state: EditorPresentation, onAdjustTime: () -> Unit,
    onAction: (EditorAction) -> Unit, modifier: Modifier,
) {
    LazyColumn(modifier, contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        item {
            Text(song.name, style = MaterialTheme.typography.titleMedium)
            Text("原始 ${formatTime(song.originalDurationUs)} · 已保留 ${formatTime(song.editedDurationUs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${state.previewLabel} · 未做响度均衡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            state.error?.let { NoteRow(it, AppIcon.ERROR) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("当前播放时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(editorTime(state.positionUs), style = MaterialTheme.typography.headlineSmall)
                }
                Text("/ ${formatTime(state.durationUs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            // 当前没有真实波形生产者。只给一条提示，不能用示例线条填满工作台。
            NoteRow("波形分析暂未接入", AppIcon.WAVE)
        }
        item {
            when (val stage = state.stage) {
                EditorStage.Ready -> {
                    if (state.busy) NoteRow("正在准备试听或保存编辑…")
                    else if (state.editedDurationUs == 0L && state.editingReady) NoteRow("已无保留片段，可从更多编辑中撤销或恢复")
                    else if (!state.playbackReady && state.error == null) NoteRow("正在准备音频…")
                    else if (state.playbackReady) NoteRow(if (state.originalPreview) "原曲试听中，可从更多编辑切回编辑结果" else "播放时标记不需要的片段起点", AppIcon.START)
                }
                is EditorStage.StartMarked -> StagePanel {
                    SectionHeader("已标记开始", icon = AppIcon.START)
                    Text(editorTime(stage.startUs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("继续播放，在片段结束时标记结束。", style = MaterialTheme.typography.bodyMedium)
                    TextAction("取消标记", AppIcon.CLOSE, { onAction(EditorAction.CANCEL_MARK) }, enabled = state.editingReady)
                }
                is EditorStage.RangeMarked -> StagePanel {
                    SectionHeader("已选中片段", icon = AppIcon.CUT)
                    Text("${editorTime(stage.startUs)} — ${editorTime(stage.endUs)}", style = MaterialTheme.typography.titleSmall)
                    Text("选区时长 ${editorTime((stage.endUs - stage.startUs).coerceAtLeast(0))}", style = MaterialTheme.typography.bodySmall)
                    ActionPair(
                        { SecondaryActionButton("试听选区", AppIcon.PLAY, { onAction(EditorAction.PREVIEW_RANGE) }, it, state.playbackReady) },
                        { SecondaryActionButton("调整时间", AppIcon.TUNE, onAdjustTime, it, state.editingReady) },
                    )
                    PrimaryActionButton("删除这段", AppIcon.DELETE, { onAction(EditorAction.DELETE_RANGE) }, Modifier.fillMaxWidth(), state.editingReady)
                    TextAction("取消标记", AppIcon.CLOSE, { onAction(EditorAction.CANCEL_MARK) }, enabled = state.editingReady)
                }
                is EditorStage.AtSeam -> StagePanel {
                    SectionHeader(stage.message, icon = AppIcon.CHECK)
                    Text("${if (state.isPlaying) "接缝位置" else "已暂停在接缝"} ${editorTime(stage.positionUs)}", style = MaterialTheme.typography.bodyMedium)
                    ActionPair(
                        { SecondaryActionButton("试听接缝", AppIcon.SPLIT, { onAction(EditorAction.PREVIEW_SEAM) }, it, state.playbackReady) },
                        { SecondaryActionButton("撤销", AppIcon.UNDO, { onAction(EditorAction.UNDO) }, it, state.editingReady && state.canUndo) },
                    )
                    PrimaryActionButton("继续播放", AppIcon.PLAY, { onAction(EditorAction.RESUME) }, Modifier.fillMaxWidth(), state.playbackReady)
                }
            }
        }
    }
}

@Composable
private fun StagePanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small), content = content)
    }
}

@Composable
private fun EditorControls(state: EditorPresentation, onAction: (EditorAction) -> Unit) {
    ActionDock {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            SeekControl("后退 5 秒", AppIcon.BACKWARD, { onAction(EditorAction.BACKWARD) }, state.playbackReady, Modifier.weight(1f))
            PrimaryActionButton(if (state.isPlaying) "暂停" else "播放", if (state.isPlaying) AppIcon.PAUSE else AppIcon.PLAY,
                { onAction(EditorAction.PLAY_PAUSE) }, Modifier.weight(1f).heightIn(min = 56.dp), state.playbackReady)
            SeekControl("前进 5 秒", AppIcon.FORWARD, { onAction(EditorAction.FORWARD) }, state.playbackReady, Modifier.weight(1f))
        }
        ActionPair(
            first = { SecondaryActionButton("标记开始", AppIcon.START, { onAction(EditorAction.MARK_START) }, it, state.editingReady && state.playbackReady && state.canMark) },
            second = { SecondaryActionButton("标记结束", AppIcon.END, { onAction(EditorAction.MARK_END) }, it,
                state.editingReady && state.playbackReady && state.canMark && state.stage is EditorStage.StartMarked) },
            stackAt = 240.dp, stackForLargeText = false,
        )
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
