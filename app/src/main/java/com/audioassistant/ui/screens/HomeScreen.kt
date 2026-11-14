package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.components.AppIcon
import com.audioassistant.ui.components.FeatureCard
import com.audioassistant.ui.components.FeatureTone
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun HomeScreen(recentSongCount: Int, onVideo: () -> Unit, onEdit: () -> Unit) {
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        item {
            Column(Modifier.padding(bottom = AppSpacing.small), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                Text("想要做什么？", style = MaterialTheme.typography.titleLarge)
                Text("选择功能，开始处理音频", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { FeatureCard("视频转音频", "提取视频声音，保存为 MP3", AppIcon.VIDEO, FeatureTone.BLUE, onVideo) }
        item { FeatureCard("音频裁剪与拼接", "裁剪歌曲，自由排序与拼接", AppIcon.CUT, FeatureTone.PURPLE, onEdit) }
        if (recentSongCount > 0) item {
            Card(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(AppSpacing.large), horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    AppGlyph(AppIcon.HISTORY, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text("继续上次剪辑", style = MaterialTheme.typography.titleSmall)
                        Text("已保存 $recentSongCount 首歌曲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppGlyph(AppIcon.RIGHT, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
