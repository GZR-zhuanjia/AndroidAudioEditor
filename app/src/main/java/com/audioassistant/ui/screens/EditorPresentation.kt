package com.audioassistant.ui.screens

/** 仅为界面展示契约，时间为成品时间线微秒；不在 UI 内推进阶段或生成播放/标记事件。 */
sealed interface EditorStage {
    data object Ready : EditorStage
    data class StartMarked(val startUs: Long) : EditorStage
    data class RangeMarked(val startUs: Long, val endUs: Long) : EditorStage
    data class AtSeam(val positionUs: Long, val message: String = "已删除选区") : EditorStage
}

data class EditorPresentation(
    val stage: EditorStage = EditorStage.Ready,
    val positionUs: Long? = null,
    val isPlaying: Boolean = false,
    val playbackReady: Boolean = false,
    val editingReady: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val canMark: Boolean = false,
    val songId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val durationUs: Long = 0,
    val editedDurationUs: Long = 0,
    val previewLabel: String = "编辑结果",
    val originalPreview: Boolean = false,
    val kept: List<KeptPartUi> = emptyList(),
    val deleted: List<DeletedItemUi> = emptyList(),
)

enum class EditorAction(val label: String) {
    PLAY_PAUSE("播放 / 暂停"), BACKWARD("后退 5 秒"), FORWARD("前进 5 秒"),
    MARK_START("标记开始"), MARK_END("标记结束"), PREVIEW_RANGE("试听选区"),
    DELETE_RANGE("删除这段"), CANCEL_MARK("取消标记"), PREVIEW_SEAM("试听接缝"),
    UNDO("撤销"), REDO("重做"), RESUME("继续播放"), SPLIT("分割"), KEEP_RANGE("仅保留选区"),
    ORIGINAL("原曲试听"), EDITED("编辑结果试听"), RESTORE_ALL("恢复原曲全部内容"),
}

data class DeletedItemUi(val id: String, val durationUs: Long)

data class WorkspacePreviewUi(val playing: Boolean = false, val busy: Boolean = false, val error: String? = null)

data class KeptPartUi(val sourceStartUs: Long, val sourceEndUs: Long, val positionUs: Long)
