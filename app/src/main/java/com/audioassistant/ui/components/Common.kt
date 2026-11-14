package com.audioassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

fun formatTime(durationUs: Long?): String {
    if (durationUs == null) return "时长未知"
    val seconds = durationUs.coerceAtLeast(0) / 1_000_000
    val hours = seconds / 3600
    return if (hours > 0) String.format(Locale.CHINA, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60)
}

fun formatSize(bytes: Long?): String = when {
    bytes == null -> "大小未知"
    bytes >= 1_048_576 -> String.format(Locale.CHINA, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0)
    else -> "$bytes 字节"
}

val PagePadding = PaddingValues(20.dp)

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StateBanner(loading: String?, error: String?, onDismissError: () -> Unit) {
    if (loading != null) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text(loading)
        }
    }
    if (error != null) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("操作未完成", style = MaterialTheme.typography.titleMedium)
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissError) { Text("关闭提示") }
            }
        }
    }
}
