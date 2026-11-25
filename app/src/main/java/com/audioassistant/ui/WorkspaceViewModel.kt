package com.audioassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audioassistant.data.FileCopyProgress
import com.audioassistant.data.FileWorkspaceRepository
import com.audioassistant.data.MediaImporter
import com.audioassistant.data.PreparedSource
import com.audioassistant.data.LibraryRepository
import com.audioassistant.data.ResultFileService
import com.audioassistant.data.SharePayload
import com.audioassistant.data.UriGrantLedger
import com.audioassistant.playback.ResultPreview
import com.audioassistant.model.AudioSegment
import com.audioassistant.model.ExportSettings
import com.audioassistant.model.ResultOrigin
import com.audioassistant.model.SongEdit
import com.audioassistant.model.SourceReference
import com.audioassistant.model.SourceStorage
import com.audioassistant.model.LibraryRecord
import com.audioassistant.processing.AudioProcessingService
import com.audioassistant.processing.ProcessingProgress
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class VideoItemUi(
    val id: String, val name: String, val uri: String,
    val durationUs: Long? = null, val sizeBytes: Long? = null,
    val status: String = "待转换", val progress: Float? = null, val error: String? = null, val isProcessing: Boolean = false,
)
data class SongItemUi(
    val id: String, val name: String, val originalDurationUs: Long, val editedDurationUs: Long,
    val error: String? = null,
)
enum class ResultCategory(val label: String) { ALL("全部"), VIDEO("视频转换"), MERGE("拼接成果") }
data class ResultItemUi(
    val id: String, val name: String, val durationUs: Long, val category: ResultCategory,
    val sizeBytes: Long? = null, val error: String? = null, val inProject: Boolean = false,
)
enum class ResultDialogKind { RENAME, DELETE }
data class ResultDialogUi(val id: String, val kind: ResultDialogKind)
data class FileOperationUi(val generation: Long, val label: String, val fraction: Float = 0f, val cancelling: Boolean = false)
enum class AppSheet { VIDEO_NAMES, EXPORT, MORE_EDIT, IMPORT_RESULTS }
data class UiNotice(val title: String, val message: String)
enum class ProcessingKind { VIDEO, EXPORT }
data class ProcessingUi(
    val kind: ProcessingKind, val label: String, val fraction: Float? = null,
    val overall: Float? = null, val index: Int = 1, val count: Int = 1, val cancelling: Boolean = false, val generation: Long = 0,
)
data class WorkspaceUiState(
    val videos: List<VideoItemUi> = emptyList(), val songs: List<SongItemUi> = emptyList(),
    val results: List<ResultItemUi> = emptyList(), val category: ResultCategory = ResultCategory.ALL,
    val exportSettings: ExportSettings = ExportSettings(), val videoNames: Map<String, String> = emptyMap(),
    val sheet: AppSheet? = null, val notice: UiNotice? = null,
    val loadingMessage: String? = "正在读取最近项目…", val errorMessage: String? = null,
    val ready: Boolean = false, val isWorking: Boolean = false, val isReordering: Boolean = false, val cancelDragSignal: Int = 0,
    val processing: ProcessingUi? = null,
    val resultDialog: ResultDialogUi? = null, val pendingShare: SharePayload? = null,
    // 仅真实文件完成并原子登记后赋值。
    val completedResult: ResultItemUi? = null,
)

/** Activity 作用域；唯一写入队列串行处理导入/排序/设置，IO 在 Repository/Importer 内切换。 */
class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FileWorkspaceRepository(application)
    private val grants = UriGrantLedger(application)
    private val importer = MediaImporter(application, repository, grants)
    private val library = LibraryRepository(repository)
    private var processingJob: Job? = null
    private var processingSequence = 0L
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    private val resultFiles = ResultFileService(application, repository) { message ->
        mutableState.update { it.copy(notice = UiNotice("文件清理提示", message)) }
    }
    private val mutableFileOperation = MutableStateFlow<FileOperationUi?>(null)
    val fileOperation = mutableFileOperation.asStateFlow()
    private var fileJob: Job? = null
    private var fileSequence = 0L
    private val processingService = AudioProcessingService(application, repository) { message ->
        mutableState.update { it.copy(notice = UiNotice("临时文件清理", message)) }
    }
    val state = mutableState.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var pending = 0
    val editor = AudioEditor(application, repository, viewModelScope, ::renderProject)
    val resultPreview = ResultPreview(application, repository, viewModelScope, ::reportError)
    private var sourceErrors: Map<String, String> = emptyMap()
    private var resultErrors: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            try {
                repository.load()
                if (!repository.cleanupProcessingResidue()) reportError("部分上次未完成的处理文件暂未清理，将在下次打开时重试。")
                if (!resultFiles.cleanupShares(startup = true)) reportError("部分过期分享副本暂未清理")
                checkSources()
                resultErrors = library.errors()
                try { reconcileGrants() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { reportError("部分无引用授权暂未释放，有效项目授权会保留。") }
                renderProject()
                mutableState.update { it.copy(ready = true, exportSettings = repository.snapshot.value.project.exportSettings, loadingMessage = if (pending > 0) "正在处理已选择的文件…" else null) }
                if (!cleanupWorkspaceFiles()) reportError("部分无引用副本未能清理，下次打开时会重试。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.update { it.copy(ready = false, loadingMessage = null, errorMessage = "最近项目无法读取：${error.message ?: error.javaClass.simpleName}。原文件已保留，已停止项目写入；请保留数据并反馈此提示。") }
            }
            for (command in commands) command()
        }
    }

    private fun enqueue(
        label: String, showLoading: Boolean = true, onRejected: (suspend () -> Unit)? = null,
        action: suspend () -> Unit,
    ) {
        // ActivityResult 可能在进程重建、项目加载完成前送达；先排队，读取失败时再拒绝写入。
        val rejection = when {
            state.value.processing != null || fileJob != null -> "文件正在处理中，请完成或取消后再修改列表。"
            !state.value.ready && state.value.loadingMessage == null -> "最近项目尚未正确读取，已停止写入，请重新打开应用并保留错误提示。"
            else -> null
        }
        if (rejection != null) {
            reportError(rejection)
            if (onRejected != null) viewModelScope.launch { onRejected() }
            return
        }
        pending++
        // 自动保存仍串行执行并锁定冲突操作，但不向页面插入会推挤列表的加载行。
        // 静默任务排队时保留当前任务或初始化的提示，到实际执行时再清除。
        mutableState.update { it.copy(isWorking = true, loadingMessage = if (pending == 1 && showLoading) label else it.loadingMessage) }
        commands.trySend {
            try {
                if (!state.value.ready) {
                    onRejected?.invoke()
                    error("最近项目读取失败，请重新选择文件前先处理读取问题")
                }
                mutableState.update { it.copy(loadingMessage = label.takeIf { showLoading }) }
                action()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                renderProject()
                reportError("操作未保存：${error.message ?: "文件读写失败"}。已有项目文件没有被覆盖为无效数据。")
            } finally {
                try { if (state.value.ready) reconcileGrants() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { reportError("部分无引用授权暂未释放，有效项目授权会保留。") }
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
            results = snapshot.results.map { it.ui() },
            completedResult = old.completedResult?.let { completed -> snapshot.results.firstOrNull { it.id == completed.id }?.ui() },
            resultDialog = old.resultDialog?.takeIf { dialog -> snapshot.results.any { it.id == dialog.id } },
        ) }
    }

    private suspend fun checkSources() {
        sourceErrors = repository.snapshot.value.project.sources.filterNot { importer.available(it) }
            .associate { it.id to "源文件不可用，可能已移动、断开连接或读取授权失效。请重新选择该文件，或从项目移除。" }
    }

    private suspend fun reconcileGrants() {
        val protectedUris = repository.snapshot.value.project.sources.filter { it.storage == SourceStorage.PERSISTED_URI }.map { it.location }.toSet() +
            state.value.videos.map { it.uri }
        grants.releaseUnused(protectedUris)
    }

    private suspend fun cleanupWorkspaceFiles(): Boolean {
        val sources = repository.cleanupSources()
        val results = repository.cleanupResults()
        return sources && results
    }

    fun refreshSources() { if (state.value.processing == null && fileJob == null) enqueue("正在检查文件…") {
        checkSources(); resultErrors = library.errors(); renderProject()
    } }

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
        editor.stopWorkspacePreview()
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
                    prepared?.let { candidate -> withContext(NonCancellable) { importer.discard(candidate) } }
                }
            }
            checkSources()
            renderProject()
            showImportMessages(messages)
            if (!cleanupWorkspaceFiles()) reportError("部分无引用副本暂未清理，下次打开会重试。")
        }
    }

    fun replaceSongSource(songId: String, uri: Uri) = enqueue("正在重新关联源文件…") {
        editor.stopWorkspacePreview()
        val project = repository.snapshot.value.project
        val song = project.songs.first { it.id == songId }
        val old = project.sources.first { it.id == song.sourceId }
        val media = importer.inspect(uri, video = false)
        require(project.sources.none { it.id != old.id && (it.originalUri == media.uri || it.location == media.uri) }) { "该文件已被另一首歌曲使用" }
        val referencedParts = project.songs.filter { it.sourceId == old.id }.flatMap { songEdit ->
            songEdit.segments + songEdit.deleted.flatMap { it.segments } +
                (songEdit.undo + songEdit.redo).flatMap { it.segments + it.deleted.flatMap { removed -> removed.segments } }
        }
        require(referencedParts.all { it.sourceEndUs <= requireNotNull(media.durationUs) }) {
            "新选择的文件短于已有剪辑或历史范围，已保留原引用和剪辑数据"
        }
        val prepared = importer.prepareSong(media)
        val replacement = prepared.copy(source = prepared.source.copy(id = old.id))
        try {
            repository.update { current -> current.copy(project = current.project.copy(
                sources = current.project.sources.map { if (it.id == old.id) replacement.source else it },
            )) }
        } finally { withContext(NonCancellable) { importer.discard(replacement) } }
        checkSources()
        renderProject()
        importer.discard(PreparedSource(old))
        if (!cleanupWorkspaceFiles()) reportError("源文件已重新关联；部分旧副本暂未清理。")
    }

    private fun showImportMessages(messages: List<String>) {
        if (messages.isNotEmpty()) mutableState.update { it.copy(notice = UiNotice("导入提示", messages.take(6).joinToString("\n") + if (messages.size > 6) "\n另有 ${messages.size - 6} 项未添加" else "")) }
    }

    fun removeVideo(id: String) = enqueue("正在移除视频…") {
        mutableState.update { it.copy(videos = it.videos.filterNot { video -> video.id == id }, videoNames = it.videoNames - id) }
    }

    fun removeSong(id: String) = enqueue("正在移除歌曲并保存…") {
        editor.stopWorkspacePreview()
        val previous = repository.snapshot.value.project.sources
        repository.update { current ->
            val songs = current.project.songs.filterNot { it.id == id }
            val used = songs.map { it.sourceId }.toSet() + songs.flatMap { it.segments.map { segment -> segment.sourceId } }
            current.copy(project = current.project.copy(songs = songs, sources = current.project.sources.filter { it.id in used }))
        }
        renderProject()
        previous.filter { old -> repository.snapshot.value.project.sources.none { it.id == old.id } }.forEach { source ->
            // URI账本会保护项目、视频队列与最近保存目录；这里只清内部实体。
            importer.discard(PreparedSource(source))
        }
        if (!cleanupWorkspaceFiles()) reportError("歌曲已移除；部分无引用内部副本暂未清理，下次打开会重试。")
    }

    fun reorderSongs(ids: List<String>) {
        if (state.value.processing != null || fileJob != null) return
        val current = state.value.songs
        if (ids == current.map { it.id }) return
        if (ids.size != current.size || ids.toSet() != current.map { it.id }.toSet()) return
        val byId = current.associateBy { it.id }
        mutableState.update { it.copy(songs = ids.map(byId::getValue)) }
        editor.stopWorkspacePreview()
        enqueue("正在保存歌曲顺序…", showLoading = false) {
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
        editor.stopWorkspacePreview()
        val result = repository.snapshot.value.results.first { it.id == id }
        val file = repository.internalFile(result.relativePath)
        require(file.isFile && file.length() == result.sizeBytes) { "成果文件缺失或大小已改变" }
        val project = repository.snapshot.value.project
        require(project.sources.none { it.storage == SourceStorage.INTERNAL_FILE && it.location == result.relativePath }) { "该成果已在歌曲列表中" }
        val source = SourceReference(UUID.randomUUID().toString(), result.displayName, SourceStorage.INTERNAL_FILE, result.relativePath,
            result.durationUs, mimeType = "audio/mpeg", sizeBytes = result.sizeBytes)
        repository.update { current -> current.copy(project = current.project.copy(
            sources = current.project.sources + source,
            songs = current.project.songs + SongEdit(UUID.randomUUID().toString(), source.id, listOf(AudioSegment(source.id, 0, source.originalDurationUs))),
        )) }
        renderProject()
    }

    fun showSheet(sheet: AppSheet) {
        if (state.value.processing != null || fileJob != null) return
        mutableState.update { current -> current.copy(sheet = sheet, videoNames = if (sheet == AppSheet.VIDEO_NAMES) current.videos.associate { video ->
            video.id to (current.videoNames[video.id] ?: video.name.substringBeforeLast('.', video.name))
        } else current.videoNames) }
    }
    fun closeSheet() { mutableState.update { it.copy(sheet = null) } }
    fun selectCategory(category: ResultCategory) { mutableState.update { it.copy(category = category) } }
    fun setExportName(name: String) {
        if (state.value.processing != null) return
        mutableState.update { it.copy(exportSettings = it.exportSettings.copy(baseName = name)) }
        persistSettings()
    }
    fun setNormalize(enabled: Boolean) {
        if (state.value.processing != null) return
        mutableState.update { it.copy(exportSettings = it.exportSettings.copy(normalizeLoudness = enabled)) }
        persistSettings()
    }
    private fun persistSettings() {
        val settings = state.value.exportSettings
        enqueue("正在保存导出设置…", showLoading = false) { repository.update { it.copy(project = it.project.copy(exportSettings = settings)) } }
    }
    fun setVideoName(id: String, name: String) { if (state.value.processing == null) mutableState.update { it.copy(videoNames = it.videoNames + (id to name)) } }

    private fun canProcess(): Boolean = state.value.let {
        it.ready && !it.isWorking && !it.isReordering && it.processing == null && processingJob == null
    }
    private fun LibraryRecord.ui() = ResultItemUi(id, displayName, durationUs,
        if (origin == ResultOrigin.VIDEO) ResultCategory.VIDEO else ResultCategory.MERGE, sizeBytes, resultErrors[id],
        repository.snapshot.value.project.sources.any { it.storage == SourceStorage.INTERNAL_FILE && it.location == relativePath })

    fun onResultAction(id: String, action: String) {
        if (!state.value.ready || state.value.isWorking || state.value.isReordering) { reportError("正在处理文件，请稍后再试"); return }
        if (state.value.results.none { it.id == id }) { reportError("成果已移除"); return }
        when (action) {
            "试听成果" -> { editor.pauseForBackground(); editor.stopWorkspacePreview(); resultPreview.toggle(id) }
            "重命名" -> mutableState.update { it.copy(resultDialog = ResultDialogUi(id, ResultDialogKind.RENAME), errorMessage = null) }
            "删除" -> mutableState.update { it.copy(resultDialog = ResultDialogUi(id, ResultDialogKind.DELETE)) }
            "分享" -> startFileOperation("正在准备分享副本") { report ->
                val payload = resultFiles.prepareShare(id, report)
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(pendingShare = payload) }
            }
            "添加到拼接" -> addResultToProject(id)
        }
    }

    fun closeResultDialog() { mutableState.update { it.copy(resultDialog = null) } }
    fun renameResult(id: String, name: String) = enqueue("正在重命名成果…") {
        library.rename(id, name)
        renderProject()
        state.value.results.firstOrNull { it.id == id }?.let { resultPreview.renamed(id, it.name) }
        closeResultDialog()
    }
    fun deleteResult(id: String) {
        resultPreview.stop(id)
        enqueue("正在移除成果…") {
            val outcome = library.remove(id)
            closeResultDialog(); resultErrors = library.errors(); renderProject()
            if (!outcome.cleaned) mutableState.update { it.copy(notice = UiNotice("已从成果库移除", "部分无引用内部文件暂未能删除，将在稍后清理。项目引用文件与外部副本仍保留。")) }
            else if (outcome.retained) mutableState.update { it.copy(notice = UiNotice("已从成果库移除", "该文件仍被项目或正在进行的操作引用，暂时保留；引用释放后再清理。外部副本不受影响。")) }
        }
    }
    fun consumeShare(token: String) { mutableState.update { if (it.pendingShare?.token == token) it.copy(pendingShare = null) else it } }

    private fun startFileOperation(
        label: String, onRejected: (suspend () -> Unit)? = null,
        operation: suspend ((FileCopyProgress) -> Unit) -> Unit,
    ) = enqueue(label, showLoading = false, onRejected = onRejected) {
        coroutineScope {
            val generation = ++fileSequence
            mutableFileOperation.value = FileOperationUi(generation, label)
            mutableState.update { it.copy(completedResult = null, errorMessage = null) }
            // 系统保存界面可在项目加载或文件检查期间返回。排队完成后才开始复制，取消仅作用于子任务。
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    operation { progress -> mutableFileOperation.update { current ->
                        if (current == null || current.generation != generation || current.cancelling) current
                        else current.copy(label = progress.label, fraction = progress.fraction)
                    } }
                } catch (_: CancellationException) {
                    reportError("已取消文件操作，内部成果仍保留。")
                } catch (error: Exception) {
                    reportError(error.message ?: "文件操作失败，内部成果仍保留。")
                }
            }
            fileJob = job
            try {
                job.join()
            } finally {
                mutableFileOperation.value = null
                fileJob = null
            }
        }
    }
    fun saveResultCopy(id: String, destination: Uri) {
        startFileOperation("正在准备另存", onRejected = { resultFiles.discardCopy(destination) }) { progress ->
            resultFiles.saveCopy(id, destination, progress) { name ->
                mutableState.update { it.copy(notice = UiNotice("另存完成", "已保存 $name，内容已核对。")) }
            }
        }
    }
    fun cancelFileOperation() {
        mutableFileOperation.update { it?.copy(cancelling = true, label = "正在取消并清理本次副本…") }
        fileJob?.cancel()
    }

    fun startConversions(onlyId: String? = null) {
        if (!canProcess()) return
        val videos = state.value.videos.filter { if (onlyId == null) it.status != "已完成" else it.id == onlyId && it.error != null }
        if (videos.isEmpty()) { reportError("没有待转换的视频"); return }
        val names = videos.associate { it.id to (state.value.videoNames[it.id] ?: it.name.substringBeforeLast('.', it.name)).trim() }
        if (names.values.any { it.isBlank() }) { reportError("请为每个视频输入文件名"); return }
        editor.stopWorkspacePreview()
        val generation = ++processingSequence
        mutableState.update { it.copy(sheet = null, completedResult = null, isWorking = true,
            processing = ProcessingUi(ProcessingKind.VIDEO, "正在准备转换", count = videos.size, generation = generation), errorMessage = null) }
        processingJob = viewModelScope.launch {
            var last: ResultItemUi? = null
            var succeeded = 0; var failed = 0; var cancelled = false
            var currentId: String? = null
            try {
                videos.forEachIndexed { index, video ->
                    currentId = video.id
                    mutableState.update { state -> state.copy(videos = state.videos.map { if (it.id == video.id) it.copy(status = "转换中", error = null, progress = null, isProcessing = true) else it }) }
                    try {
                        processingService.convert(Uri.parse(video.uri), names.getValue(video.id), { progress ->
                            mutableState.update { state ->
                                val active = state.processing
                                if (active == null || active.generation != generation || active.cancelling) state else state.copy(
                                    processing = active.copy(label = progress.label, fraction = progress.fraction,
                                        overall = (index + (progress.fraction ?: 0f)) / videos.size, index = index + 1),
                                    videos = state.videos.map { if (it.id == video.id) it.copy(status = progress.label, progress = progress.fraction) else it },
                                )
                            }
                        }) { result ->
                            // Called in the non-cancellable commit section, so a committed file is never reported as lost.
                            last = result.ui(); succeeded++
                            mutableState.update { state -> state.copy(
                                videos = state.videos.map { if (it.id == video.id) it.copy(status = "已完成", progress = null, error = null, isProcessing = false) else it },
                                results = repository.snapshot.value.results.map { it.ui() },
                            ) }
                        }
                    } catch (stopped: CancellationException) { throw stopped }
                    catch (error: Exception) {
                        failed++
                        mutableState.update { state -> state.copy(videos = state.videos.map {
                            if (it.id == video.id) it.copy(status = "转换失败", progress = null, error = error.message ?: "无法处理此视频", isProcessing = false) else it
                        }) }
                    }
                }
            } catch (_: CancellationException) { cancelled = true }
            finally {
                renderProject()
                mutableState.update { state -> state.copy(isWorking = pending > 0, processing = null, completedResult = last,
                    videos = state.videos.map { if (cancelled && it.id == currentId && it.status != "已完成" && it.error == null) it.copy(status = "已取消", progress = null, isProcessing = false) else it },
                    errorMessage = if (cancelled) "已取消，保留 $succeeded 个成功成果" else if (failed > 0) "转换结束：成功 $succeeded 个，失败 $failed 个，可在列表重试" else state.errorMessage,
                ) }
                processingJob = null
            }
        }
    }

    fun retryVideo(id: String) = startConversions(id)

    fun startExport() {
        if (!canProcess()) return
        val project = repository.snapshot.value.project
        val settings = state.value.exportSettings
        if (settings.baseName.isBlank() || project.songs.isEmpty() || project.songs.any { it.segments.isEmpty() } || state.value.songs.any { it.error != null }) {
            reportError("请填写文件名，并确认每首歌曲来源有效、至少保留一段音频"); return
        }
        editor.stopWorkspacePreview()
        val generation = ++processingSequence
        mutableState.update { it.copy(sheet = null, completedResult = null, isWorking = true, errorMessage = null,
            processing = ProcessingUi(ProcessingKind.EXPORT, "正在准备导出", generation = generation)) }
        processingJob = viewModelScope.launch {
            var result: ResultItemUi? = null
            try {
                processingService.export(project, settings.baseName, settings.normalizeLoudness, { progress: ProcessingProgress ->
                    mutableState.update {
                        val active = it.processing
                        if (active == null || active.generation != generation || active.cancelling) it
                        else it.copy(processing = active.copy(label = progress.label, fraction = progress.fraction, overall = progress.fraction))
                    }
                }) { record -> result = record.ui(); mutableState.update { it.copy(results = repository.snapshot.value.results.map { item -> item.ui() }) } }
            } catch (_: CancellationException) { reportError(if (result == null) "已取消导出，已有成果和原曲不受影响" else "成果已保存，取消未删除已提交的文件") }
            catch (error: Exception) { reportError("导出失败：${error.message ?: "音频处理错误"}。已有成果已保留。") }
            finally {
                renderProject()
                mutableState.update { it.copy(isWorking = pending > 0, processing = null, completedResult = result) }
                processingJob = null
            }
        }
    }

    fun cancelProcessing() {
        mutableState.update { it.copy(processing = it.processing?.copy(cancelling = true, label = "正在取消并清理未完成文件…")) }
        processingJob?.cancel()
    }
    fun unavailable(action: String) { mutableState.update { it.copy(notice = UiNotice("暂不可用", "当前版本尚未开放“$action”。没有处理或更改任何音频文件。")) } }
    fun reportError(message: String) { mutableState.update { it.copy(errorMessage = message) } }
    fun dismissError() { mutableState.update { it.copy(errorMessage = null) } }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
    fun dismissSuccess() { mutableState.update { it.copy(completedResult = null) } }
    override fun onCleared() { fileJob?.cancel(); resultPreview.release(); processingJob?.cancel(); processingService.close(); editor.release(); super.onCleared() }
}
