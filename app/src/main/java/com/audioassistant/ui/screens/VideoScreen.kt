package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.audioassistant.ui.VideoItemUi
import com.audioassistant.ui.components.ActionDock
import com.audioassistant.ui.components.ActionPair
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.FeatureTone
import com.audioassistant.ui.components.IconBadge
import com.audioassistant.ui.components.NoteRow
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.PrimaryActionButton
import com.audioassistant.ui.components.SecondaryActionButton
import com.audioassistant.ui.components.SectionHeader
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatSize
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun VideoScreen(
    videos: List<VideoItemUi>, enabled: Boolean, onPhoto: () -> Unit, onFiles: () -> Unit,
    onSettings: () -> Unit, onRemove: (String) -> Unit, onUnavailable: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (videos.isNotEmpty()) Column(Modifier.padding(horizontal = AppSpacing.page), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            SectionHeader("待转换", "${videos.size} 个视频", AppIcon.VIDEO)
            ActionPair(
                { SecondaryActionButton("从相册添加", AppIcon.PHOTO, onPhoto, it, enabled) },
                { SecondaryActionButton("从文件添加", AppIcon.FOLDER, onFiles, it, enabled) },
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            if (videos.isEmpty()) item {
                EmptyState("添加需要转换的视频", "选择一个或多个视频，留下想听的声音。", icon = AppIcon.VIDEO, tone = FeatureTone.BLUE) {
                    PrimaryActionButton("从相册选择", AppIcon.PHOTO, onPhoto, Modifier.fillMaxWidth(), enabled)
                    SecondaryActionButton("从文件中选择", AppIcon.FOLDER, onFiles, Modifier.fillMaxWidth(), enabled)
                }
                NoteRow("默认 MP3 · 成果自动保存到成果库", AppIcon.AUDIO)
                NoteRow("转换功能暂未开放")
            }
            items(videos, key = { it.id }) { video ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(AppIcon.VIDEO, FeatureTone.BLUE, AppSpacing.touch)
                            Column(Modifier.weight(1f)) {
                                Text(video.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${formatTime(video.durationUs)} · ${formatSize(video.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusIcon = when {
                                video.error != null -> AppIcon.ERROR
                                video.progress != null -> AppIcon.WAVE
                                video.status == "已完成" -> AppIcon.CHECK
                                else -> AppIcon.HISTORY
                            }
                            AppGlyph(statusIcon, modifier = Modifier.size(AppSpacing.large), tint = if (video.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                            Text(video.status, Modifier.weight(1f).padding(start = AppSpacing.small), style = MaterialTheme.typography.bodySmall)
                            TextAction("移除", AppIcon.CLOSE, { onRemove(video.id) }, enabled = enabled)
                        }
                        video.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
                        video.error?.let {
                            NoteRow(it, AppIcon.ERROR)
                            TextAction("重试", AppIcon.HISTORY, { onUnavailable("重试转换") })
                        }
                    }
                }
            }
        }
        if (videos.isNotEmpty()) ActionDock {
            NoteRow("转换功能暂未开放 · 可先调整名称")
            PrimaryActionButton("转换设置与命名", AppIcon.TUNE, onSettings, Modifier.fillMaxWidth(), enabled)
        }
    }
}
