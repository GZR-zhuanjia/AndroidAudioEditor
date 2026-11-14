package com.audioassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

// 首页卡片与底栏使用同一 Destination，不建立第二份页面状态。
enum class Destination(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "首页", Icons.Default.Home),
    VIDEO("video", "视频转换", Icons.Default.PlayArrow),
    EDIT("edit", "音频剪辑", Icons.Default.Edit),
    LIBRARY("library", "成果库", Icons.Default.List),
}

const val EDITOR_ROUTE = "editor?songId={songId}"
