package com.audioassistant.data

import com.audioassistant.model.EditProject
import kotlinx.coroutines.flow.StateFlow

/** 最近项目存取契约；FileWorkspaceRepository 提供串行原子实现。 */
interface RecentProjectRepository {
    val project: StateFlow<EditProject?>
    suspend fun load()
    suspend fun save(project: EditProject)
}
