package com.audioassistant.model

import kotlinx.serialization.Serializable

@Serializable
enum class ResultOrigin { VIDEO, MERGE }

/** 只有临时输出完成并通过真实MP3探测后才能登记；普通导入不生成记录。 */
@Serializable
data class LibraryRecord(
    val id: String,
    val displayName: String,
    val relativePath: String,
    val durationUs: Long,
    val sizeBytes: Long,
    val origin: ResultOrigin,
)

/** 项目与成果引用共用同一份原子快照，避免分开写入导致引用关系不一致。 */
@Serializable
data class WorkspaceSnapshot(
    val schemaVersion: Int = 1,
    val project: EditProject = EditProject(),
    val results: List<LibraryRecord> = emptyList(),
)
