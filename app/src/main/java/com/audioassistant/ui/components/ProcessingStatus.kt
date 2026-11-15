package com.audioassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.ProcessingKind
import com.audioassistant.ui.ProcessingUi

@Composable
fun ProcessingStatus(state: ProcessingUi, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state.label, style = MaterialTheme.typography.titleSmall)
        if (state.overall != null) LinearProgressIndicator(progress = { state.overall.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(if (state.kind == ProcessingKind.VIDEO) "第 ${state.index} / ${state.count} 个视频 · 已完成的成果会保留" else "导出到内部成果库 · 取消不会影响原曲",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SecondaryActionButton(if (state.cancelling) "正在取消…" else "取消处理", AppIcon.CLOSE, onCancel, Modifier.fillMaxWidth(), !state.cancelling)
    }
}
