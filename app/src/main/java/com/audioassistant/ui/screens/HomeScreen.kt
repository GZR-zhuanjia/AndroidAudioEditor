package com.audioassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.components.PagePadding

@Composable
fun HomeScreen(hasRecentProject: Boolean, onVideo: () -> Unit, onEdit: () -> Unit) {
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("让声音，恰到好处", style = MaterialTheme.typography.headlineSmall)
                Text("转换、裁剪与拼接，都在手机里完成。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { FeatureCard("视频转音频", "从视频中提取声音，保存为 MP3。", "进入视频转换", onVideo) }
        item { FeatureCard("音频裁剪与拼接", "听着剪掉不需要的部分，再按顺序拼接。", "进入音频剪辑", onEdit) }
        if (hasRecentProject) {
            item { TextButton(onClick = onEdit) { Text("继续上次剪辑") } }
        }
        item {
            Text("完全离线 · 无广告 · 原始文件不改动", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}
