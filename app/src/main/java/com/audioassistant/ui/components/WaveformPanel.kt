package com.audioassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.audioassistant.model.AudioSegment
import com.audioassistant.ui.screens.EditorPresentation
import com.audioassistant.ui.screens.EditorStage
import com.audioassistant.waveform.WaveformData
import com.audioassistant.waveform.WaveformPhase
import com.audioassistant.waveform.WaveformState
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class Bar(val peak: Float, val rms: Float)

/** Auxiliary timeline: tap seeks, pinch zooms and horizontal drag pans. Button editing stays primary. */
@Composable
fun WaveformPanel(
    waveform: WaveformState, editor: EditorPresentation, onSeek: (Long) -> Unit,
    onRetry: () -> Unit, onCancel: () -> Unit,
) {
    when (waveform.phase) {
        WaveformPhase.EMPTY -> NoteRow("正在准备波形分析…", AppIcon.WAVE)
        WaveformPhase.LOADING -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("分析真实音频", icon = AppIcon.WAVE)
            if (waveform.totalUs > 0) {
                val progress = (waveform.processedUs.toDouble() / waveform.totalUs).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${(progress * 100).toInt()}% · 可继续播放与剪辑", style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在读取音轨，可继续播放与剪辑", style = MaterialTheme.typography.bodySmall)
            }
            TextAction("取消分析", AppIcon.CLOSE, onCancel)
        }
        WaveformPhase.ERROR, WaveformPhase.CANCELLED -> Column {
            NoteRow(waveform.error ?: "已取消波形分析", if (waveform.error == null) AppIcon.WAVE else AppIcon.ERROR)
            TextAction("重新分析", AppIcon.WAVE, onRetry)
        }
        WaveformPhase.READY -> {
            val data = waveform.result?.data ?: return
            val parts = remember(waveform.parts, editor.originalPreview, data) {
                if (editor.originalPreview) listOf(AudioSegment("original", 0, data.durationUs)) else waveform.parts
            }
            val total = remember(parts) { parts.sumOf { it.durationUs } }
            if (total <= 0) { NoteRow("已无保留音频，恢复片段后显示波形", AppIcon.WAVE); return }
            WaveformViewport(data, parts, total, editor, onSeek)
        }
    }
}

@Composable
private fun WaveformViewport(
    data: WaveformData, parts: List<AudioSegment>, total: Long, editor: EditorPresentation, onSeek: (Long) -> Unit,
) {
    var zoom by rememberSaveable(editor.songId, editor.originalPreview) { mutableStateOf(1f) }
    var start by rememberSaveable(editor.songId, editor.originalPreview) { mutableLongStateOf(0L) }
    var width by remember { mutableIntStateOf(0) }
    val visible = (total / zoom).toLong().coerceIn(minOf(total, 100_000L), total)
    val from = start.coerceIn(0, total - visible)
    val barCount = (width / 4).coerceIn(1, 1024)
    val bars by produceState<List<Bar>>(emptyList(), data, parts, from, visible, barCount) {
        value = withContext(Dispatchers.Default) { project(data, parts, from, visible, barCount) }
    }
    val colors = MaterialTheme.colorScheme
    val gain = if (data.maximum > 0f) (0.9f / data.maximum).coerceIn(0.01f, 16f) else 1f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (editor.originalPreview) "原曲波形" else "编辑波形", style = MaterialTheme.typography.titleSmall)
            Row {
                TextButton(onClick = { zoom = (zoom / 2).coerceAtLeast(1f) }, enabled = zoom > 1f) { Text("缩小") }
                TextButton(onClick = {
                    zoom = (zoom * 2).coerceAtMost(64f)
                    start = ((editor.positionUs ?: from) - total / zoom / 2).roundToLong().coerceAtLeast(0)
                }, enabled = zoom < 64f) { Text("放大") }
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
            Canvas(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 8.dp, vertical = 12.dp)
                .semantics { contentDescription = "真实音频峰值与RMS波形，可通过播放和时间按钮精确定位" }
                .onSizeChanged { width = it.width }
                .pointerInput(from, visible, editor.editingReady) {
                    detectTapGestures { point -> if (editor.editingReady && size.width > 0) onSeek(from + (point.x / size.width * visible).toLong()) }
                }
                .pointerInput(total) {
                    detectTransformGestures { centroid, pan, scale, _ ->
                        if (size.width > 0) {
                            val oldVisible = (total / zoom).toLong().coerceIn(minOf(total, 100_000L), total)
                            val oldStart = start.coerceIn(0, total - oldVisible)
                            val fraction = (centroid.x / size.width).coerceIn(0f, 1f)
                            val anchor = oldStart + oldVisible * fraction
                            zoom = (zoom * scale).coerceIn(1f, 64f)
                            val newVisible = (total / zoom).toLong().coerceIn(minOf(total, 100_000L), total)
                            start = (anchor - newVisible * fraction - pan.x / size.width * newVisible)
                                .roundToLong().coerceIn(0, total - newVisible)
                        }
                    }
                }) {
                fun x(time: Long): Float = ((time - from).toDouble() / visible * size.width).toFloat().coerceIn(0f, size.width)
                if (!editor.originalPreview) {
                    val range = editor.stage as? EditorStage.RangeMarked
                    if (range != null && range.endUs > from && range.startUs < from + visible) {
                        drawRect(colors.primary.copy(alpha = 0.13f), Offset(x(range.startUs), 0f), Size(x(range.endUs) - x(range.startUs), size.height))
                    }
                    val marker = (editor.stage as? EditorStage.StartMarked)?.startUs ?: range?.startUs
                    if (marker != null && marker in from..from + visible) drawLine(colors.secondary, Offset(x(marker), 0f), Offset(x(marker), size.height), 2.dp.toPx())
                }
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.22f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                val step = size.width / barCount
                bars.forEachIndexed { index, bar ->
                    val center = Offset((index + 0.5f) * step, size.height / 2)
                    val peak = (bar.peak * gain).coerceIn(0f, 1f) * size.height / 2
                    val rms = (bar.rms * gain).coerceIn(0f, 1f) * size.height / 2
                    // Silence remains zero: no minimum bar height or per-bar normalization.
                    if (peak > 0) drawLine(colors.primary.copy(alpha = 0.4f), center.copy(y = center.y - peak), center.copy(y = center.y + peak), (step * 0.6f).coerceAtLeast(1f))
                    if (rms > 0) drawLine(colors.primary, center.copy(y = center.y - rms), center.copy(y = center.y + rms), (step * 0.6f).coerceAtLeast(1f))
                }
                editor.positionUs?.takeIf { it in from..from + visible }?.let {
                    drawLine(colors.tertiary, Offset(x(it), 0f), Offset(x(it), size.height), 2.dp.toPx())
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(from), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(formatTime(from + visible), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Text("峰值 / RMS · 显示倍率 ${String.format(Locale.CHINA, "%.1f", gain)}× · 点按定位，双指缩放、拖动平移",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

private suspend fun project(data: WaveformData, parts: List<AudioSegment>, from: Long, visible: Long, count: Int): List<Bar> {
    val ends = LongArray(parts.size)
    var duration = 0L
    parts.forEachIndexed { index, part -> duration += part.durationUs; ends[index] = duration }
    return List(count) { index ->
        kotlin.coroutines.coroutineContext.ensureActive()
        val left = from + visible * index / count
        val right = from + visible * (index + 1) / count
        var p = ends.binarySearch(left).let { if (it < 0) -it - 1 else it + 1 }
        var peak = 0f; var squares = 0.0; var cursor = left
        while (p < parts.size && cursor < right) {
            val partStart = if (p == 0) 0L else ends[p - 1]
            val end = minOf(right, ends[p])
            val sourceStart = parts[p].sourceStartUs + cursor - partStart
            val (a, b) = data.amplitudes(sourceStart, sourceStart + end - cursor)
            peak = maxOf(peak, a); squares += b.toDouble() * b * (end - cursor)
            cursor = end; ++p
        }
        Bar(peak, if (right > left) kotlin.math.sqrt(squares / (right - left)).toFloat() else 0f)
    }
}
