package com.audioassistant.data

import android.content.Context
import android.util.AtomicFile
import com.audioassistant.model.EditProject
import com.audioassistant.model.SourceStorage
import com.audioassistant.model.WorkspaceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 仅磁盘提交成功后发布状态；损坏/未知版本不会以空项目覆盖。 */
class FileWorkspaceRepository(context: Context) : RecentProjectRepository {
    private val filesDir = context.filesDir
    private val atomic = AtomicFile(File(filesDir, "project/workspace.json"))
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private val mutableSnapshot = MutableStateFlow(WorkspaceSnapshot())
    private val mutableProject = MutableStateFlow<EditProject?>(null)
    val snapshot = mutableSnapshot.asStateFlow()
    override val project = mutableProject.asStateFlow()
    private var loaded = false

    override suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val value = if (atomic.baseFile.exists() || File(atomic.baseFile.path + ".bak").exists()) {
                atomic.openRead().bufferedReader().use { json.decodeFromString<WorkspaceSnapshot>(it.readText()) }
            } else WorkspaceSnapshot()
            validate(value)
            mutableSnapshot.value = value
            mutableProject.value = value.project
            loaded = true
        }
    }

    override suspend fun save(project: EditProject) {
        update { it.copy(project = project) }
    }

    suspend fun update(transform: (WorkspaceSnapshot) -> WorkspaceSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(loaded) { "项目尚未正确读取，已停止保存以保护原文件" }
            val next = transform(mutableSnapshot.value)
            validate(next)
            // 原子替换和状态发布作为同一不可取消临界区，避免已落盘却清理了引用文件。
            withContext(NonCancellable) {
                check(atomic.baseFile.parentFile!!.isDirectory || atomic.baseFile.parentFile!!.mkdirs())
                val bytes = json.encodeToString(next).toByteArray(Charsets.UTF_8)
                val stream = atomic.startWrite()
                try {
                    stream.write(bytes)
                    stream.fd.sync()
                    atomic.finishWrite(stream)
                } catch (error: Throwable) {
                    atomic.failWrite(stream)
                    throw error
                }
                mutableSnapshot.value = next
                mutableProject.value = next.project
            }
        }
    }

    fun internalFile(relativePath: String): File {
        require(!relativePath.startsWith('/')) { "内部路径无效" }
        val root = filesDir.canonicalFile
        val file = File(root, relativePath).canonicalFile
        require(file.relativeTo(root).invariantSeparatorsPath == relativePath) { "内部路径不是规范相对路径" }
        require(file.parentFile == File(root, "sources") || file.parentFile == File(root, "results")) { "文件不在受控目录" }
        return file
    }

    fun isReferenced(relativePath: String): Boolean = snapshot.value.let { value ->
        value.project.sources.any { it.storage == SourceStorage.INTERNAL_FILE && it.location == relativePath } ||
            value.results.any { it.relativePath == relativePath }
    }

    /** 只扫描本应用生成的 sources 文件；不扫描/删除成果、外部 URI 或未知文件。 */
    suspend fun cleanupSources(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(loaded)
            var complete = true
            File(filesDir, "sources").listFiles()?.forEach { file ->
                if (file.name.matches(Regex("[0-9a-f-]{36}\\.(source|part)")) &&
                    !isReferenced("sources/${file.name}") && !file.delete()) complete = false
            }
            complete
        }
    }

    private fun validate(value: WorkspaceSnapshot) {
        require(value.schemaVersion == 1 && value.project.schemaVersion == 1) { "不支持的项目版本" }
        val project = value.project
        val sources = project.sources.associateBy { it.id }
        require(sources.size == project.sources.size && sources.keys.none { it.isBlank() })
        require(project.songs.map { it.id }.toSet().size == project.songs.size)
        project.sources.forEach { source ->
            require(source.originalDurationUs > 0)
            if (source.storage == SourceStorage.INTERNAL_FILE) internalFile(source.location)
            else require(android.net.Uri.parse(source.location).scheme == "content")
        }
        var projectDuration = 0L
        project.songs.forEach { song ->
            require(song.id.isNotBlank() && sources.containsKey(song.sourceId))
            fun validateParts(parts: List<com.audioassistant.model.AudioSegment>): Long {
                var duration = 0L
                var previousEnd = 0L
                parts.forEach { segment ->
                    require(segment.sourceId == song.sourceId)
                    val source = sources.getValue(segment.sourceId)
                    require(segment.sourceEndUs <= source.originalDurationUs && segment.sourceStartUs >= previousEnd)
                    duration = Math.addExact(duration, segment.durationUs)
                    previousEnd = segment.sourceEndUs
                }
                return duration
            }
            fun validateDeleted(deleted: List<com.audioassistant.model.DeletedAudio>) {
                require(deleted.map { it.id }.toSet().size == deleted.size)
                deleted.forEach { require(it.id.isNotBlank() && it.segments.isNotEmpty()); validateParts(it.segments) }
            }
            val duration = validateParts(song.segments)
            require(song.resumePositionUs in 0..duration)
            validateDeleted(song.deleted)
            require(song.undo.size <= 50 && song.redo.size <= 50)
            (song.undo + song.redo).forEach { version ->
                val historicalDuration = validateParts(version.segments)
                require(version.positionUs in 0..historicalDuration)
                validateDeleted(version.deleted)
            }
            projectDuration = Math.addExact(projectDuration, duration)
        }
        require(value.results.map { it.id }.toSet().size == value.results.size)
        value.results.forEach { result ->
            require(result.id.isNotBlank() && result.relativePath.startsWith("results/"))
            internalFile(result.relativePath)
            require(result.durationUs > 0 && result.sizeBytes > 0)
        }
    }
}
