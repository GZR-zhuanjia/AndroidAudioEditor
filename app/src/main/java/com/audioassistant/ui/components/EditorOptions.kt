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
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun EditorOptions(onClose: () -> Unit) {
    var precise by rememberSaveable { mutableStateOf(true) }
    Column {
        SheetHeader("更多编辑", AppIcon.TUNE, onClose)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.page), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            FilterChip(selected = precise, onClick = { precise = true }, label = { Text("精确时间") }, leadingIcon = { AppGlyph(AppIcon.HISTORY, modifier = Modifier.size(AppSpacing.large)) })
            FilterChip(selected = !precise, onClick = { precise = false }, label = { Text("剪切与试听") }, leadingIcon = { AppGlyph(AppIcon.CUT, modifier = Modifier.size(AppSpacing.large)) })
        }
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            item { NoteRow("试听与编辑暂未开放，以下操作暂不可用") }
            if (precise) {
                listOf("开始时间" to AppIcon.START, "结束时间" to AppIcon.END).forEach { (label, icon) ->
                    item {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                SectionHeader(label, icon = icon)
                                OutlinedTextField(value = "", onValueChange = {}, enabled = false, placeholder = { Text("分:秒.毫秒") },
                                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, singleLine = true)
                                ActionPair(
                                    { TextAction("−0.1 秒", AppIcon.MINUS, {}, it, false) },
                                    { TextAction("+0.1 秒", AppIcon.PLUS, {}, it, false) },
                                )
                                TextAction("当前播放位置设为${if (label == "开始时间") "起点" else "终点"}", icon, {}, enabled = false)
                            }
                        }
                    }
                }
            } else {
                listOf(
                    "试听选区" to AppIcon.PLAY, "取消标记" to AppIcon.CLOSE,
                    "分割" to AppIcon.SPLIT, "仅保留选区" to AppIcon.CUT,
                    "撤销" to AppIcon.UNDO, "重做" to AppIcon.REDO,
                    "恢复删除内容" to AppIcon.HISTORY, "原曲试听" to AppIcon.AUDIO,
                    "编辑结果试听" to AppIcon.PLAY, "试听连接处" to AppIcon.SPLIT,
                ).forEach { (label, icon) -> item { SecondaryActionButton(label, icon, {}, Modifier.fillMaxWidth(), false) } }
            }
        }
    }
}
