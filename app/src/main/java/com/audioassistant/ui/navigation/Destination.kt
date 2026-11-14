package com.audioassistant.ui.navigation

import com.audioassistant.ui.components.AppIcon

// 首页卡片和底栏仍使用同一路由/状态；仅标签采用紧凑文字。
enum class Destination(val route: String, val title: String, val navLabel: String, val icon: AppIcon) {
    HOME("home", "首页", "首页", AppIcon.HOME),
    VIDEO("video", "视频转换", "视频", AppIcon.VIDEO),
    EDIT("edit", "音频剪辑", "剪辑", AppIcon.CUT),
    LIBRARY("library", "成果库", "成果库", AppIcon.LIBRARY),
}

const val EDITOR_ROUTE = "editor?songId={songId}"
