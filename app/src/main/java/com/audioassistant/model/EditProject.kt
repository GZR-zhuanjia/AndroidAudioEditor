package com.audioassistant.model

import kotlinx.serialization.Serializable

/** 所有时间为微秒；片段区间为 [sourceStartUs, sourceEndUs)。 */
@Serializable
data class AudioSegment(
    val sourceId: String,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
) {
    init {
        require(sourceStartUs >= 0 && sourceEndUs > sourceStartUs)
    }
    val durationUs: Long get() = sourceEndUs - sourceStartUs
}

@Serializable
enum class SourceStorage { PERSISTED_URI, INTERNAL_FILE }

/** location 为 content URI 或内部相对路径，禁止将任意绝对路径视为可清理文件。 */
@Serializable
data class SourceReference(
    val id: String,
    val displayName: String,
    val storage: SourceStorage,
    val location: String,
    val originalDurationUs: Long,
    val originalUri: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class SongEdit(
    val id: String,
    val sourceId: String,
    val segments: List<AudioSegment>,
)

@Serializable
data class ExportSettings(
    val baseName: String = "拼接的歌曲",
    val format: OutputFormat = OutputFormat.MP3,
    val normalizeLoudness: Boolean = true,
)

@Serializable
enum class OutputFormat { MP3 }

/** 仅一份最近项目；撤销栈和编辑算法在阶段 4 实现。 */
@Serializable
data class EditProject(
    val schemaVersion: Int = 1,
    val sources: List<SourceReference> = emptyList(),
    val songs: List<SongEdit> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
)
