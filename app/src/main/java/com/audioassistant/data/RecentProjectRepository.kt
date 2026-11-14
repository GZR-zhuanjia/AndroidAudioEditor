package com.audioassistant.data

import com.audioassistant.model.EditProject
import kotlinx.coroutines.flow.StateFlow

/** 阶段 3 实现串行、原子持久化与引用验证；当前仅声明契约。 */
interface RecentProjectRepository {
    val project: StateFlow<EditProject?>
    suspend fun load()
    suspend fun save(project: EditProject)
}
