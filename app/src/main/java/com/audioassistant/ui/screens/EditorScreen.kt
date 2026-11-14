package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.audioassistant.ui.components.IconBadge
import com.audioassistant.ui.components.NoteRow
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.PrimaryActionButton
import com.audioassistant.ui.components.SecondaryActionButton
import com.audioassistant.ui.components.SectionHeader
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.theme.AppSpacing

/** 仅呈现当前真实信息；播放器、选区和波形尚未接入，操作继续禁用。 */
@Composable
fun EditorScreen(song: SongItemUi?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 矮屏横向分区，不把整页套进滚动；普通竖屏主控固定底部。
        if (maxWidth > maxHeight && maxHeight < 440.dp) {
            Row {
                EditorInformation(song, Modifier.weight(1f))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { EditorControls(song != null) }
            }
        } else {
            Column {
                EditorInformation(song, Modifier.weight(1f))
                EditorControls(song != null)
            }
        }
    }
}

@Composable
private fun EditorInformation(song: SongItemUi?, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcon.AUDIO, size = AppSpacing.touch)
                Column(Modifier.weight(1f)) {
                    Text(song?.name ?: "尚未选择歌曲", style = MaterialTheme.typography.titleMedium)
                    Text(if (song == null) "请从歌曲列表选择歌曲" else "原始 ${formatTime(song.originalDurationUs)} · 已保留 ${formatTime(song.editedDurationUs)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            song?.error?.let { NoteRow(it, AppIcon.ERROR) }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                    SectionHeader("音频波形", icon = AppIcon.WAVE)
                    Text("波形尚未生成", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NoteRow("真实分析开放后显示，不影响按钮式剪辑")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("当前播放时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("— : —", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("总时长 ${song?.let { formatTime(it.editedDurationUs) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    SectionHeader("当前选区", "尚未标记", AppIcon.START)
                    Text("起点 —   终点 —   时长 —", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EditorControls(hasSong: Boolean) {
    ActionDock {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            SeekControl("后退 5 秒", AppIcon.BACKWARD, Modifier.weight(1f))
            PrimaryActionButton("播放", AppIcon.PLAY, {}, Modifier.weight(1f).heightIn(min = 64.dp), false)
            SeekControl("前进 5 秒", AppIcon.FORWARD, Modifier.weight(1f))
        }
        ActionPair(
            first = { SecondaryActionButton("标记开始", AppIcon.START, {}, it, false) },
            second = { SecondaryActionButton("标记结束", AppIcon.END, {}, it, false) },
            stackAt = 240.dp, stackForLargeText = false,
        )
        if (hasSong) PrimaryActionButton("删除这段", AppIcon.DELETE, {}, Modifier.fillMaxWidth(), false)
        NoteRow("试听与剪切暂未开放 · 标记结束后暂停")
    }
}

@Composable
private fun SeekControl(label: String, icon: AppIcon, modifier: Modifier) {
    TextButton(onClick = {}, enabled = false, modifier = modifier.heightIn(min = 64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
            AppGlyph(icon)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
