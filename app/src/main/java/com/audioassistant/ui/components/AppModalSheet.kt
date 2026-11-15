package com.audioassistant.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.theme.AppSheetShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 先完成 Material 面板收起，再同步提交关闭/业务动作，避免移除面板时取消提交。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalSheet(
    actionScope: CoroutineScope,
    onDismissRequest: () -> Unit,
    isOpen: () -> Boolean,
    content: @Composable ColumnScope.(finish: (() -> Unit) -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentIsOpen by rememberUpdatedState(isOpen)
    var finishing by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false } }
    ModalBottomSheet(
        onDismissRequest = { if (!finishing) onDismissRequest() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AppSheetShape,
        tonalElevation = 0.dp,
    ) {
        content { action ->
            if (!finishing) {
                finishing = true
                // Scope 属于根界面；action 中移除这个面板不会取消其后的业务调用。
                actionScope.launch {
                    try {
                        sheetState.hide()
                        if (active && !sheetState.isVisible) {
                            action()
                            // 业务校验/并发保护拒绝提交时保留原面板和错误，不留下隐藏的模态层。
                            if (active && currentIsOpen()) sheetState.show()
                        }
                    } finally {
                        if (active && currentIsOpen()) finishing = false
                    }
                }
            }
        }
    }
}
