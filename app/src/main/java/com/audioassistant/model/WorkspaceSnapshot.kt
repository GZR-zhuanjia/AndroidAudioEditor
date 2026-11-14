package com.audioassistant.model

import kotlinx.serialization.Serializable

@Serializable
enum class ResultOrigin { VIDEO, MERGE }

/** 仅正式成果可登记；导入文件永远不会生成该记录。阶段 6/7 接通写入。 */
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
