package com.audioassistant.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.audioassistant.ui.screens.EditorAction
import com.audioassistant.ui.screens.EditorPresentation
import com.audioassistant.ui.screens.EditorStage
import com.audioassistant.ui.theme.AppSpacing
import java.util.Locale

@Composable
fun EditorOptions(
    state: EditorPresentation, onClose: () -> Unit, onAction: (EditorAction) -> Unit,
    onInput: (Boolean, String) -> Unit, onAdjust: (Boolean, Long) -> Unit,
    onCurrent: (Boolean) -> Unit, onRestore: (String) -> Unit, onSeek: (String) -> Unit,
) {
    var precise by rememberSaveable { mutableStateOf(true) }
    Column {
        SheetHeader("更多编辑", AppIcon.TUNE, onClose)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            FilterChip(selected = precise, onClick = { precise = true }, label = { Text("精确时间") }, leadingIcon = { AppGlyph(AppIcon.HISTORY, modifier = Modifier.size(AppSpacing.large)) })
            FilterChip(selected = !precise, onClick = { precise = false }, label = { Text("剪切与试听") }, leadingIcon = { AppGlyph(AppIcon.CUT, modifier = Modifier.size(AppSpacing.large)) })
        }
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            state.error?.let { message -> item { NoteRow(message, AppIcon.ERROR) } }
            if (state.busy) item { NoteRow("正在保存或准备音频…") }
            if (precise) {
                if (state.originalPreview) item { NoteRow("当前是原曲时间，设置起止点时映射到编辑结果。") }
                item {
                    var text by rememberSaveable(state.songId) { mutableStateOf("") }
                    OutlinedTextField(value = text, onValueChange = { text = it }, enabled = state.editingReady,
                        label = { Text("跳转时间（秒或分:秒）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSeek(text) }))
                    TextAction("跳转并暂停", AppIcon.PLAY, { onSeek(text) }, enabled = state.editingReady)
                }
                listOf(true, false).forEach { start ->
                    item {
                        val range = state.stage as? EditorStage.RangeMarked
                        val begun = state.stage as? EditorStage.StartMarked
                        val value = if (start) range?.startUs ?: begun?.startUs else range?.endUs
                        var text by rememberSaveable(state.songId, start, value) { mutableStateOf(value?.let(::preciseTime).orEmpty()) }
                        val label = if (start) "开始时间" else "结束时间"
                        val icon = if (start) AppIcon.START else AppIcon.END
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                SectionHeader(label, icon = icon)
                                OutlinedTextField(value = text, onValueChange = { text = it }, enabled = state.editingReady,
                                    placeholder = { Text("例如 01:23.200") }, modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small, singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { onInput(start, text) }))
                                TextAction("应用${if (start) "起点" else "终点"}", AppIcon.CHECK, { onInput(start, text) }, enabled = state.editingReady)
                                ActionPair(
                                    { TextAction("−0.1 秒", AppIcon.MINUS, { onAdjust(start, -100_000) }, it, state.editingReady && value != null) },
                                    { TextAction("+0.1 秒", AppIcon.PLUS, { onAdjust(start, 100_000) }, it, state.editingReady && value != null) },
                                )
                                TextAction("当前播放位置设为${if (start) "起点" else "终点"}", icon, { onCurrent(start) }, enabled = state.editingReady)
                            }
                        }
                    }
                }
            } else {
                val range = state.stage is EditorStage.RangeMarked
                val actions = listOf(
                    Triple(EditorAction.PREVIEW_RANGE, AppIcon.PLAY, range && state.playbackReady),
                    Triple(EditorAction.CANCEL_MARK, AppIcon.CLOSE, state.stage is EditorStage.StartMarked || range),
                    Triple(EditorAction.SPLIT, AppIcon.SPLIT, state.editedDurationUs > 0),
                    Triple(EditorAction.KEEP_RANGE, AppIcon.CUT, range),
                    Triple(EditorAction.UNDO, AppIcon.UNDO, state.canUndo),
                    Triple(EditorAction.REDO, AppIcon.REDO, state.canRedo),
                    Triple(EditorAction.ORIGINAL, AppIcon.AUDIO, true),
                    Triple(EditorAction.EDITED, AppIcon.PLAY, state.editedDurationUs > 0),
                    Triple(EditorAction.PREVIEW_SEAM, AppIcon.SPLIT, state.editedDurationUs > 0),
                )
                actions.forEach { (action, icon, possible) -> item {
                    SecondaryActionButton(action.label, icon, { onAction(action) }, Modifier.fillMaxWidth(), state.editingReady && possible)
                } }
                item { NoteRow("普通试听未做响度均衡；接缝试听播放当前位置前后各2秒") }
                if (state.kept.isNotEmpty()) {
                    item { SectionHeader("保留片段", "${state.kept.size} 段", AppIcon.CUT) }
                    state.kept.forEachIndexed { index, part -> item(key = "kept:$index") {
                        Column {
                            Text("原曲 ${preciseTime(part.sourceStartUs)} — ${preciseTime(part.sourceEndUs)}", style = MaterialTheme.typography.bodySmall)
                            TextAction("跳至片段 ${index + 1}", AppIcon.PLAY, { onSeek(preciseTime(part.positionUs)) }, enabled = state.editingReady)
                        }
                    } }
                }
                if (state.deleted.isNotEmpty()) {
                    item { SectionHeader("恢复删除内容", "${state.deleted.size} 次删除", AppIcon.HISTORY) }
                    state.deleted.forEachIndexed { index, deleted -> item(key = "deleted:${deleted.id}") {
                        SecondaryActionButton("恢复第${index + 1}次删除 · ${formatTime(deleted.durationUs)}", AppIcon.HISTORY,
                            { onRestore(deleted.id) }, Modifier.fillMaxWidth(), state.editingReady)
                    } }
                    item { TextAction("恢复原曲全部内容", AppIcon.HISTORY, { onAction(EditorAction.RESTORE_ALL) }, enabled = state.editingReady) }
                }
            }
        }
    }
}

private fun preciseTime(timeUs: Long): String = String.format(Locale.CHINA, "%02d:%02d.%06d", timeUs / 60_000_000, timeUs / 1_000_000 % 60, timeUs % 1_000_000)
