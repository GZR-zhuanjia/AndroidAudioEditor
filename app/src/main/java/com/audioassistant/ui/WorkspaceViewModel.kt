package com.audioassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audioassistant.data.FileWorkspaceRepository
import com.audioassistant.data.MediaImporter
import com.audioassistant.data.PreparedSource
import com.audioassistant.model.AudioSegment
import com.audioassistant.model.ExportSettings
import com.audioassistant.model.ResultOrigin
import com.audioassistant.model.SongEdit
import com.audioassistant.model.SourceReference
import com.audioassistant.model.SourceStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class VideoItemUi(
    val id: String, val name: String, val uri: String,
    val durationUs: Long? = null, val sizeBytes: Long? = null,
    val status: String = "待转换", val progress: Float? = null, val error: String? = null,
)
data class SongItemUi(
    val id: String, val name: String, val originalDurationUs: Long, val editedDurationUs: Long,
    val error: String? = null,
)
enum class ResultCategory(val label: String) { ALL("全部"), VIDEO("视频转换"), MERGE("拼接成果") }
data class ResultItemUi(val id: String, val name: String, val durationUs: Long, val category: ResultCategory)
enum class AppSheet { VIDEO_NAMES, EXPORT, MORE_EDIT }
data class UiNotice(val title: String, val message: String)
data class WorkspaceUiState(
    val videos: List<VideoItemUi> = emptyList(), val songs: List<SongItemUi> = emptyList(),
    val results: List<ResultItemUi> = emptyList(), val category: ResultCategory = ResultCategory.ALL,
    val exportSettings: ExportSettings = ExportSettings(), val videoNames: Map<String, String> = emptyMap(),
    val sheet: AppSheet? = null, val notice: UiNotice? = null,
    val loadingMessage: String? = "正在读取最近项目…", val errorMessage: String? = null,
    val ready: Boolean = false, val isWorking: Boolean = false, val isReordering: Boolean = false, val cancelDragSignal: Int = 0,
    // 只有未来真实任务验证成果后才可赋值；没有模拟成功事件。
    val completedResult: ResultItemUi? = null,
)

/** Activity 作用域；唯一写入队列串行处理导入/排序/设置，IO 在 Repository/Importer 内切换。 */
class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileWorkspaceRepository(application)
    private val importer = MediaImporter(application, repository)
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    val state = mutableState.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var pending = 0
    private var sourceErrors: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            try {
                repository.load()
                checkSources()
                renderProject()
                mutableState.update { it.copy(ready = true, exportSettings = repository.snapshot.value.project.exportSettings, loadingMessage = if (pending > 0) "正在处理已选择的文件…" else null) }
                if (!repository.cleanupSources()) reportError("部分无引用副本未能清理，下次打开时会重试。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.update { it.copy(ready = false, loadingMessage = null, errorMessage = "最近项目无法读取：${error.message ?: error.javaClass.simpleName}。原文件已保留，已停止项目写入；请保留数据并反馈此提示。") }
            }
            for (command in commands) command()
        }
    }

    private fun enqueue(label: String, action: suspend () -> Unit) {
        // ActivityResult 可能在进程重建、项目加载完成前送达；先排队，读取失败时再拒绝写入。
        if (!state.value.ready && state.value.loadingMessage == null) {
            reportError("最近项目尚未正确读取，已停止写入，请重新打开应用并保留错误提示。")
            return
        }
        pending++
        mutableState.update { it.copy(isWorking = true, loadingMessage = if (pending == 1) label else it.loadingMessage) }
        commands.trySend {
            try {
                check(state.value.ready) { "最近项目读取失败，请重新选择文件前先处理读取问题" }
                mutableState.update { it.copy(loadingMessage = label) }
                action()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                renderProject()
                reportError("操作未保存：${error.message ?: "文件读写失败"}。已有项目文件没有被覆盖为无效数据。")
            } finally {
                pending--
                if (pending == 0) mutableState.update { it.copy(isWorking = false, loadingMessage = null) }
            }
        }
    }

    private fun renderProject() {
        val snapshot = repository.snapshot.value
        val sources = snapshot.project.sources.associateBy { it.id }
        mutableState.update { old -> old.copy(
            songs = snapshot.project.songs.map { song ->
                val source = sources.getValue(song.sourceId)
                SongItemUi(song.id, source.displayName, source.originalDurationUs,
                    song.segments.fold(0L) { total, segment -> Math.addExact(total, segment.durationUs) }, sourceErrors[source.id])
            },
            results = snapshot.results.map { result -> ResultItemUi(result.id, result.displayName, result.durationUs,
                if (result.origin == ResultOrigin.VIDEO) ResultCategory.VIDEO else ResultCategory.MERGE) },
        ) }
    }

    private suspend fun checkSources() {
        sourceErrors = repository.snapshot.value.project.sources.filterNot { importer.available(it) }
            .associate { it.id to "源文件不可用，可能已移动、断开连接或读取授权失效。请重新选择该文件，或从项目移除。" }
    }

    fun refreshSources() = enqueue("正在检查源文件…") { checkSources(); renderProject() }

    fun importVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        enqueue("正在读取视频信息…") {
            val messages = mutableListOf<String>()
            for (uri in uris.distinct()) {
                if (state.value.videos.any { it.uri == uri.normalizeScheme().toString() }) {
                    messages += "已跳过重复视频"; continue
                }
                try {
                    val media = importer.inspect(uri, video = true)
                    mutableState.update { it.copy(videos = it.videos + VideoItemUi(UUID.randomUUID().toString(), media.name, media.uri, media.durationUs, media.sizeBytes)) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { messages += "视频未添加：${error.message ?: "无法读取文件"}" }
            }
            showImportMessages(messages)
        }
    }

    fun importSongs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        enqueue("正在添加歌曲并保存项目…") {
            val messages = mutableListOf<String>()
            for (uri in uris.distinct()) {
                val identity = uri.normalizeScheme().toString()
                val existing = repository.snapshot.value.project.sources.firstOrNull { it.originalUri == identity || it.location == identity }
                if (existing != null) {
                    messages += "已跳过重复歌曲：${existing.displayName}"; continue
                }
                var prepared: PreparedSource? = null
                try {
                    prepared = importer.prepareSong(importer.inspect(uri, video = false))
                    val source = prepared.source
                    repository.update { current ->
                        val song = SongEdit(UUID.randomUUID().toString(), source.id, listOf(AudioSegment(source.id, 0, source.originalDurationUs)))
                        current.copy(project = current.project.copy(sources = current.project.sources + source, songs = current.project.songs + song))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { messages += "歌曲未添加：${error.message ?: "无法读取或保存文件"}" }
                finally {
                    prepared?.let { candidate -> withContext(NonCancellable) { importer.discard(candidate, state.value.videos.map { it.uri }.toSet()) } }
                }
            }
            checkSources()
            renderProject()
            showImportMessages(messages)
            if (!repository.cleanupSources()) reportError("部分无引用副本暂未清理，下次打开会重试。")
        }
    }

    fun replaceSongSource(songId: String, uri: Uri) = enqueue("正在重新关联源文件…") {
        val project = repository.snapshot.value.project
        val song = project.songs.first { it.id == songId }
        val old = project.sources.first { it.id == song.sourceId }
        val media = importer.inspect(uri, video = false)
        require(project.sources.none { it.id != old.id && (it.originalUri == media.uri || it.location == media.uri) }) { "该文件已被另一首歌曲使用" }
        require(project.songs.filter { it.sourceId == old.id }.flatMap { it.segments }.all { it.sourceEndUs <= requireNotNull(media.durationUs) }) {
            "新选择的文件短于已有剪辑范围，已保留原引用和剪辑数据"
        }
        val prepared = importer.prepareSong(media)
        val replacement = prepared.copy(source = prepared.source.copy(id = old.id))
        try {
            repository.update { current -> current.copy(project = current.project.copy(
                sources = current.project.sources.map { if (it.id == old.id) replacement.source else it },
            )) }
        } finally { withContext(NonCancellable) { importer.discard(replacement, state.value.videos.map { it.uri }.toSet()) } }
        checkSources()
        renderProject()
        if (state.value.videos.none { it.uri == old.location }) importer.discard(PreparedSource(old, newlyGranted = true))
        if (!repository.cleanupSources()) reportError("源文件已重新关联；部分旧副本暂未清理。")
    }

    private fun showImportMessages(messages: List<String>) {
        if (messages.isNotEmpty()) mutableState.update { it.copy(notice = UiNotice("导入提示", messages.take(6).joinToString("\n") + if (messages.size > 6) "\n另有 ${messages.size - 6} 项未添加" else "")) }
    }

    fun removeVideo(id: String) {
        mutableState.update { it.copy(videos = it.videos.filterNot { video -> video.id == id }, videoNames = it.videoNames - id) }
    }

    fun removeSong(id: String) = enqueue("正在移除歌曲并保存…") {
        val previous = repository.snapshot.value.project.sources
        repository.update { current ->
            val songs = current.project.songs.filterNot { it.id == id }
            val used = songs.map { it.sourceId }.toSet() + songs.flatMap { it.segments.map { segment -> segment.sourceId } }
            current.copy(project = current.project.copy(songs = songs, sources = current.project.sources.filter { it.id in used }))
        }
        renderProject()
        previous.filter { old -> repository.snapshot.value.project.sources.none { it.id == old.id } }.forEach { source ->
            // 视频会话仍用到同一 URI 时，保守保留其读取授权。
            if (state.value.videos.none { it.uri == source.location }) importer.discard(PreparedSource(source, newlyGranted = true))
        }
        if (!repository.cleanupSources()) reportError("歌曲已移除；部分无引用内部副本暂未清理，下次打开会重试。")
    }

    fun reorderSongs(ids: List<String>) {
        val current = state.value.songs
        if (ids == current.map { it.id }) return
        if (ids.size != current.size || ids.toSet() != current.map { it.id }.toSet()) return
        val byId = current.associateBy { it.id }
        mutableState.update { it.copy(songs = ids.map(byId::getValue)) }
        enqueue("正在保存歌曲顺序…") {
            repository.update { snapshot ->
                val songs = snapshot.project.songs.associateBy { it.id }
                require(ids.toSet() == songs.keys)
                snapshot.copy(project = snapshot.project.copy(songs = ids.map(songs::getValue)))
            }
            renderProject()
        }
    }

    fun moveSong(id: String, delta: Int) {
        val ids = state.value.songs.map { it.id }.toMutableList()
        val index = ids.indexOf(id)
        val target = index + delta
        if (index < 0 || target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        reorderSongs(ids)
    }

    fun requestCancelDrag() { mutableState.update { it.copy(cancelDragSignal = it.cancelDragSignal + 1) } }

    fun setReordering(active: Boolean) { mutableState.update { it.copy(isReordering = active) } }

    fun addResultToProject(id: String) = enqueue("正在添加成果并保存项目…") {
        val result = repository.snapshot.value.results.first { it.id == id }
        val file = repository.internalFile(result.relativePath)
        require(file.isFile && file.length() == result.sizeBytes) { "成果文件缺失或大小已改变" }
        val project = repository.snapshot.value.project
        require(project.sources.none { it.storage == SourceStorage.INTERNAL_FILE && it.location == result.relativePath }) { "该成果已在歌曲列表中" }
        val source = SourceReference(UUID.randomUUID().toString(), result.displayName, SourceStorage.INTERNAL_FILE, result.relativePath, result.durationUs)
        repository.update { current -> current.copy(project = current.project.copy(
            sources = current.project.sources + source,
            songs = current.project.songs + SongEdit(UUID.randomUUID().toString(), source.id, listOf(AudioSegment(source.id, 0, source.originalDurationUs))),
        )) }
        renderProject()
    }

    fun showSheet(sheet: AppSheet) {
        mutableState.update { current -> current.copy(sheet = sheet, videoNames = if (sheet == AppSheet.VIDEO_NAMES) current.videos.associate { video ->
            video.id to (current.videoNames[video.id] ?: video.name.substringBeforeLast('.', video.name))
        } else current.videoNames) }
    }
    fun closeSheet() { mutableState.update { it.copy(sheet = null) } }
    fun selectCategory(category: ResultCategory) { mutableState.update { it.copy(category = category) } }
    fun setExportName(name: String) {
        mutableState.update { it.copy(exportSettings = it.exportSettings.copy(baseName = name)) }
        persistSettings()
    }
    fun setNormalize(enabled: Boolean) {
        mutableState.update { it.copy(exportSettings = it.exportSettings.copy(normalizeLoudness = enabled)) }
        persistSettings()
    }
    private fun persistSettings() {
        val settings = state.value.exportSettings
        enqueue("正在保存导出设置…") { repository.update { it.copy(project = it.project.copy(exportSettings = settings)) } }
    }
    fun setVideoName(id: String, name: String) { mutableState.update { it.copy(videoNames = it.videoNames + (id to name)) } }
    fun unavailable(action: String) { mutableState.update { it.copy(notice = UiNotice("暂不可用", "当前版本尚未开放“$action”。没有处理或更改任何音频文件。")) } }
    fun reportError(message: String) { mutableState.update { it.copy(errorMessage = message) } }
    fun dismissError() { mutableState.update { it.copy(errorMessage = null) } }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
    fun dismissSuccess() { mutableState.update { it.copy(completedResult = null) } }
}
