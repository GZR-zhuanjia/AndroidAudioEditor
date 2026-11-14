package com.audioassistant.ui.screens

/** 仅为界面展示契约，时间为成品时间线微秒；不在 UI 内推进阶段或生成播放/标记事件。 */
sealed interface EditorStage {
    data object Ready : EditorStage
    data class StartMarked(val startUs: Long) : EditorStage
    data class RangeMarked(val startUs: Long, val endUs: Long) : EditorStage
    data class AtSeam(val positionUs: Long) : EditorStage
}

data class EditorPresentation(
    val stage: EditorStage = EditorStage.Ready,
    val positionUs: Long? = null,
    val isPlaying: Boolean = false,
    val playbackReady: Boolean = false,
    val editingReady: Boolean = false,
    val canUndo: Boolean = false,
)

enum class EditorAction(val label: String) {
    PLAY_PAUSE("播放 / 暂停"), BACKWARD("后退 5 秒"), FORWARD("前进 5 秒"),
    MARK_START("标记开始"), MARK_END("标记结束"), PREVIEW_RANGE("试听选区"),
    DELETE_RANGE("删除这段"), CANCEL_MARK("取消标记"), PREVIEW_SEAM("试听接缝"),
    UNDO("撤销"), RESUME("继续播放"),
}
