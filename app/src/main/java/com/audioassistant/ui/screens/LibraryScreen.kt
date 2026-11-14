package com.audioassistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.ResultCategory
import com.audioassistant.ui.ResultItemUi
import com.audioassistant.ui.components.EmptyState
import com.audioassistant.ui.components.PagePadding
import com.audioassistant.ui.components.formatTime

@Composable
fun LibraryScreen(results: List<ResultItemUi>, category: ResultCategory, onCategory: (ResultCategory) -> Unit, onAction: (String) -> Unit) {
    val filtered = results.filter { category == ResultCategory.ALL || it.category == category }
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("完成的声音，都在这里", style = MaterialTheme.typography.titleLarge)
            Text("仅收录视频转换和拼接成果，普通导入歌曲不会加入。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultCategory.entries.forEach { value ->
                    FilterChip(selected = category == value, onClick = { onCategory(value) }, label = { Text(value.label) })
                }
            }
        }
        if (filtered.isEmpty()) {
            item { EmptyState(if (category == ResultCategory.ALL) "还没有成果" else "暂无${category.label}", "成功转换或导出的音频会保存在这里。关闭完成面板不会删除成果。") }
        }
        items(filtered, key = { it.id }) { result -> ResultCard(result, onAction) }
    }
}

@Composable
private fun ResultCard(result: ResultItemUi, onAction: (String) -> Unit) {
    var expanded by rememberSaveable(result.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(result.name, style = MaterialTheme.typography.titleMedium)
            Text("${result.category.label} · ${formatTime(result.durationUs)}")
            Row {
                TextButton(onClick = { onAction("试听成果") }) { Text("试听") }
                Column {
                    TextButton(onClick = { expanded = true }) { Text("更多操作") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("重命名", "分享", "另存", "添加到拼接", "删除").forEach { action ->
                            DropdownMenuItem(text = { Text(action) }, onClick = { expanded = false; onAction(action) })
                        }
                    }
                }
            }
        }
    }
}
