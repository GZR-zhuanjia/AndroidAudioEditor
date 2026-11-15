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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.VideoItemUi
import com.audioassistant.ui.ProcessingUi
import com.audioassistant.ui.ProcessingKind
import com.audioassistant.ui.components.ProcessingStatus
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
import com.audioassistant.ui.components.TextAction
import com.audioassistant.ui.components.formatSize
import com.audioassistant.ui.components.formatTime
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun VideoScreen(
    videos: List<VideoItemUi>, enabled: Boolean, onPhoto: () -> Unit, onFiles: () -> Unit,
    onSettings: () -> Unit, onRemove: (String) -> Unit, onRetry: (String) -> Unit,
    processing: ProcessingUi?, onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (videos.isNotEmpty()) Column(Modifier.padding(horizontal = AppSpacing.page)) {
            ActionPair(
                { SecondaryActionButton("从相册添加", AppIcon.PHOTO, onPhoto, it, enabled) },
                { SecondaryActionButton("从文件添加", AppIcon.FOLDER, onFiles, it, enabled) },
                stackAt = 0.dp, stackForLargeText = false,
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            if (videos.isEmpty()) item {
                EmptyState("添加需要转换的视频", "选择一个或多个视频，成果保存到成果库。", icon = AppIcon.VIDEO, tone = FeatureTone.BLUE) {
                    PrimaryActionButton("从相册选择", AppIcon.PHOTO, onPhoto, Modifier.fillMaxWidth(), enabled)
                    SecondaryActionButton("从文件中选择", AppIcon.FOLDER, onFiles, Modifier.fillMaxWidth(), enabled)
                    NoteRow("默认 MP3 · 成果自动保存")
                }
            }
            items(videos, key = { it.id }) { video ->
                VideoFileItem(video, enabled, { onRemove(video.id) }, { onRetry(video.id) })
            }
        }
        if (videos.isNotEmpty()) ActionDock {
            if (processing?.kind == ProcessingKind.VIDEO) ProcessingStatus(processing, onCancel)
            else {
                NoteRow("${videos.size} 个视频 · 成功后自动加入成果库")
                PrimaryActionButton("转换设置与命名", AppIcon.TUNE, onSettings, Modifier.fillMaxWidth(), enabled && videos.any { it.status != "已完成" })
            }
        }
    }
}

@Composable
private fun VideoFileItem(video: VideoItemUi, enabled: Boolean, onRemove: () -> Unit, onRetry: () -> Unit) {
    // 普通待转换项仅显示名称与元数据，移除的48dp触摸区与它们并排。
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(start = AppSpacing.medium, end = AppSpacing.xSmall, top = AppSpacing.medium, bottom = AppSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcon.VIDEO, FeatureTone.BLUE, 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                    Text(video.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${formatTime(video.durationUs)} · ${formatSize(video.sizeBytes)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove, enabled = enabled) {
                    AppGlyph(AppIcon.CLOSE, "移除视频：${video.name}", modifier = Modifier.size(20.dp))
                }
            }
            // 无实际进度/错误且仍为默认待转换时，不重复展示状态行或伪进度。
            val showStatus = video.error != null || video.progress != null || video.status != "待转换"
            if (showStatus) {
                Row(Modifier.padding(end = AppSpacing.small), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    val statusIcon = when {
                        video.error != null -> AppIcon.ERROR
                        video.isProcessing -> AppIcon.WAVE
                        video.status == "已完成" -> AppIcon.CHECK
                        else -> AppIcon.HISTORY
                    }
                    AppGlyph(statusIcon, modifier = Modifier.size(AppSpacing.large),
                        tint = if (video.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                    Text(video.status, style = MaterialTheme.typography.bodySmall)
                }
            }
            video.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = AppSpacing.small)) }
            if (video.isProcessing && video.progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = AppSpacing.small))
            video.error?.let {
                NoteRow(it, AppIcon.ERROR)
                TextAction("重试", AppIcon.HISTORY, onRetry, enabled = enabled)
            }
        }
    }
}
