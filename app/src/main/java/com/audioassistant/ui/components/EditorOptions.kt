package com.audioassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditorOptions() {
    var precise by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("更多编辑", style = MaterialTheme.typography.titleLarge)
        Text("试听与编辑尚未开放，以下操作暂不可用。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = precise, onClick = { precise = true }, label = { Text("精确时间") })
            FilterChip(selected = !precise, onClick = { precise = false }, label = { Text("剪切与试听") })
        }
        if (precise) {
            listOf("开始时间", "结束时间").forEach { label ->
                OutlinedTextField(value = "", onValueChange = {}, enabled = false, label = { Text(label) },
                    placeholder = { Text("分:秒.毫秒") }, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = {}, enabled = false) { Text("−0.1 秒") }
                    TextButton(onClick = {}, enabled = false) { Text("+0.1 秒") }
                }
                TextButton(onClick = {}, enabled = false) { Text("当前播放位置设为${if (label == "开始时间") "起点" else "终点"}") }
            }
        } else {
            listOf(
                "分割" to "仅保留选区", "撤销" to "重做", "恢复删除内容" to "原曲试听",
                "编辑结果试听" to "试听连接处",
            ).forEach { (left, right) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text(left) }
                    TextButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text(right) }
                }
            }
        }
    }
}
