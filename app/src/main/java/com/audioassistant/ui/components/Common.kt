package com.audioassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.theme.AppSpacing
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

val PagePadding = PaddingValues(horizontal = AppSpacing.page, vertical = AppSpacing.large)

@Composable
fun StateBanner(loading: String?, error: String?, onDismissError: () -> Unit) {
    if (loading != null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(loading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (error != null) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
            Row(Modifier.padding(start = AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(AppIcon.ERROR, modifier = Modifier.size(AppSpacing.icon))
                Column(Modifier.weight(1f).padding(AppSpacing.medium)) {
                    Text("操作未完成", style = MaterialTheme.typography.titleSmall)
                    Text(error, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDismissError) { AppGlyph(AppIcon.CLOSE, "关闭错误提示") }
            }
        }
    }
}
