package com.audioassistant.data

import com.audioassistant.model.SourceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class DeleteResultOutcome(val retained: Boolean, val cleaned: Boolean)

class LibraryRepository(private val workspace: FileWorkspaceRepository) {
    suspend fun errors(): Map<String, String> = withContext(Dispatchers.IO) {
        workspace.snapshot.value.results.mapNotNull { result ->
            val file = workspace.internalFile(result.relativePath)
            if (file.isFile && file.length() == result.sizeBytes) null else result.id to "内部文件缺失或大小已改变"
        }.toMap()
    }

    suspend fun rename(id: String, name: String) {
        workspace.update { snapshot ->
            val result = snapshot.results.firstOrNull { it.id == id } ?: error("成果已移除")
            val display = ResultNames.unique(name, snapshot.results.filterNot { it.id == id }.map { it.displayName })
            snapshot.copy(results = snapshot.results.map { if (it.id == id) it.copy(displayName = display) else it },
                project = snapshot.project.copy(sources = snapshot.project.sources.map {
                    if (it.storage == SourceStorage.INTERNAL_FILE && it.location == result.relativePath) it.copy(displayName = display) else it
                }))
        }
    }

    /** Commit reference removal first. Project/active references then decide whether the file can go. */
    suspend fun remove(id: String): DeleteResultOutcome = withContext(NonCancellable + Dispatchers.IO) {
        val record = workspace.snapshot.value.results.firstOrNull { it.id == id } ?: error("成果已移除")
        workspace.update { it.copy(results = it.results.filterNot { result -> result.id == id }) }
        val retained = workspace.isReferenced(record.relativePath)
        DeleteResultOutcome(retained, workspace.cleanupResults())
    }
}
