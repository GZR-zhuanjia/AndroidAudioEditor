package com.audioassistant.ui

import androidx.lifecycle.ViewModel
import com.audioassistant.model.ExportSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 这些是视图数据契约，不生成示例文件；阶段 3 起由真实存储提供列表。
data class VideoItemUi(
    val id: String,
    val name: String,
    val durationUs: Long? = null,
    val sizeBytes: Long? = null,
    val status: String = "待转换",
    val progress: Float? = null,
    val error: String? = null,
)

data class SongItemUi(
    val id: String,
    val name: String,
    val originalDurationUs: Long,
    val editedDurationUs: Long,
)

enum class ResultCategory(val label: String) {
    ALL("全部"), VIDEO("视频转换"), MERGE("拼接成果"),
}

data class ResultItemUi(
    val id: String,
    val name: String,
    val durationUs: Long,
    val category: ResultCategory,
)

enum class AppSheet { VIDEO_NAMES, EXPORT, MORE_EDIT }
data class UiNotice(val title: String, val message: String)

data class WorkspaceUiState(
    val videos: List<VideoItemUi> = emptyList(),
    val songs: List<SongItemUi> = emptyList(),
    val results: List<ResultItemUi> = emptyList(),
    val category: ResultCategory = ResultCategory.ALL,
    val exportSettings: ExportSettings = ExportSettings(),
    val videoNames: Map<String, String> = emptyMap(),
    val sheet: AppSheet? = null,
    val notice: UiNotice? = null,
    val loadingMessage: String? = null,
    val errorMessage: String? = null,
    // 只有后续真实任务提交并验证成果后才可赋值；阶段 2 不生成成功事件。
    val completedResult: ResultItemUi? = null,
)

/** 在 NavHost 外获取，所有模块共用。当前只维护会话 UI 设置，不冒充自动存盘。 */
class WorkspaceViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    val state = mutableState.asStateFlow()

    fun showSheet(sheet: AppSheet) {
        mutableState.update { current ->
            current.copy(
                sheet = sheet,
                videoNames = if (sheet == AppSheet.VIDEO_NAMES) {
                    current.videos.associate { video ->
                        video.id to (current.videoNames[video.id] ?: video.name.substringBeforeLast('.', video.name))
                    }
                } else current.videoNames,
            )
        }
    }
    fun closeSheet() { mutableState.update { it.copy(sheet = null) } }
    fun selectCategory(category: ResultCategory) {
        mutableState.update { it.copy(category = category) }
    }
    fun setExportName(name: String) {
        mutableState.update { it.copy(exportSettings = it.exportSettings.copy(baseName = name)) }
    }
    fun setNormalize(enabled: Boolean) {
        mutableState.update { it.copy(exportSettings = it.exportSettings.copy(normalizeLoudness = enabled)) }
    }
    fun setVideoName(id: String, name: String) {
        mutableState.update { it.copy(videoNames = it.videoNames + (id to name)) }
    }
    fun unavailable(action: String) {
        mutableState.update {
            it.copy(notice = UiNotice("暂不可用", "当前版本尚未开放“$action”。没有处理或更改任何音频文件。"))
        }
    }
    fun dismissError() { mutableState.update { it.copy(errorMessage = null) } }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
    fun dismissSuccess() { mutableState.update { it.copy(completedResult = null) } }
}
