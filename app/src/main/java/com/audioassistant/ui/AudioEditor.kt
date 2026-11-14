package com.audioassistant.ui

import android.content.Context
import android.net.Uri
import com.audioassistant.data.FileWorkspaceRepository
import com.audioassistant.editing.SegmentTimeline
import com.audioassistant.editing.TimeInput
import com.audioassistant.model.AudioSegment
import com.audioassistant.model.DeletedAudio
import com.audioassistant.model.EditVersion
import com.audioassistant.model.SongEdit
import com.audioassistant.model.SourceStorage
import com.audioassistant.playback.AudioPreview
import com.audioassistant.playback.PlaybackStatus
import com.audioassistant.playback.PreviewMode
import com.audioassistant.ui.screens.DeletedItemUi
import com.audioassistant.ui.screens.EditorAction
import com.audioassistant.ui.screens.EditorPresentation
import com.audioassistant.ui.screens.EditorStage
import com.audioassistant.ui.screens.KeptPartUi
import com.audioassistant.ui.screens.WorkspacePreviewUi
import com.audioassistant.waveform.WaveformSession
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 独立于列表状态的编辑会话，播放时钟不会驱动整个 Scaffold 重组。由 WorkspaceViewModel 管理生命周期。 */
class AudioEditor(
    context: Context, private val repository: FileWorkspaceRepository,
    private val scope: CoroutineScope, private val onProjectCommitted: () -> Unit,
) {
    private val preview = AudioPreview(context)
    val waveform = WaveformSession(context, scope)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(EditorPresentation())
    val state = mutableState.asStateFlow()
    val workspaceState = state.map { WorkspacePreviewUi(it.songId == null && it.isPlaying, it.busy, if (it.songId == null) it.error else null) }
        .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, WorkspacePreviewUi())
    private var song: SongEdit? = null
    private var requestedSongId: String? = null
    private var uris = emptyMap<String, Uri>()
    private var originalDurationUs = 0L
    private var stage: EditorStage = EditorStage.Ready
    private var busy = false
    private var closing = false
    private var localError: String? = null
    private var epoch = 0L

    init { scope.launch { preview.state.collect { publish(it) } } }

    private fun publish(playback: PlaybackStatus = preview.state.value) {
        val current = song
        var cursorUs = 0L
        val kept = current?.segments?.map { part ->
            KeptPartUi(part.sourceStartUs, part.sourceEndUs, cursorUs).also { cursorUs += part.durationUs }
        }.orEmpty()
        mutableState.value = EditorPresentation(
            stage = stage, positionUs = if (current == null && requestedSongId != null) null else playback.positionUs, isPlaying = playback.playing,
            playbackReady = playback.ready && !busy && !closing, editingReady = current != null && !busy && !closing,
            canUndo = current?.undo?.isNotEmpty() == true, canRedo = current?.redo?.isNotEmpty() == true,
            canMark = playback.mode == PreviewMode.EDITED, songId = requestedSongId, busy = busy || closing,
            error = localError ?: playback.error,
            durationUs = if (playback.mode == PreviewMode.ORIGINAL) originalDurationUs else current?.let { SegmentTimeline.duration(it.segments) } ?: 0,
            editedDurationUs = current?.let { SegmentTimeline.duration(it.segments) } ?: 0,
            previewLabel = playback.mode.label, originalPreview = playback.mode == PreviewMode.ORIGINAL,
            kept = kept,
            deleted = current?.deleted?.map { DeletedItemUi(it.id, SegmentTimeline.duration(it.segments)) }.orEmpty(),
        )
    }

    private suspend fun resolveUris(ids: Set<String>): Map<String, Uri> = withContext(Dispatchers.IO) {
        repository.snapshot.value.project.sources.filter { it.id in ids }.associate { source ->
            source.id to if (source.storage == SourceStorage.PERSISTED_URI) Uri.parse(source.location)
            else {
                val file = repository.internalFile(source.location)
                require(file.isFile && file.length() > 0) { "源文件缺失，请返回列表重新选择" }
                Uri.fromFile(file)
            }
        }
    }

    fun open(id: String) {
        if (requestedSongId == id && (song != null || busy)) return
        requestedSongId = id
        waveform.clear()
        val token = ++epoch
        scope.launch {
            mutex.withLock {
                if (token != epoch) return@withLock
                busy = true; localError = null; song = null; stage = EditorStage.Ready; preview.clear(); publish()
                try {
                    val project = repository.snapshot.value.project
                    val selected = project.songs.first { it.id == id }
                    uris = resolveUris(setOf(selected.sourceId))
                    song = selected
                    originalDurationUs = project.sources.first { it.id == selected.sourceId }.originalDurationUs
                    waveform.open(id, uris.getValue(selected.sourceId), selected.segments)
                    stage = EditorStage.Ready
                    edited(selected.resumePositionUs)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { localError = error.message ?: "无法打开歌曲"; song = null }
                finally { busy = false; publish() }
            }
        }
    }

    private fun edited(positionUs: Long, play: Boolean = false) {
        val current = song ?: return
        preview.load(current.segments, uris, PreviewMode.EDITED,
            positionUs = positionUs.coerceIn(0, SegmentTimeline.duration(current.segments)), play = play)
    }
    private fun editedPosition(): Long {
        val current = song ?: return 0
        return if (preview.state.value.mode == PreviewMode.ORIGINAL) SegmentTimeline.editedPosition(current.segments, preview.positionUs())
        else preview.positionUs().coerceIn(0, SegmentTimeline.duration(current.segments))
    }
    private fun range(): Pair<Long, Long> {
        val selected = stage as? EditorStage.RangeMarked ?: error("请先标记开始和结束")
        return selected.startUs to selected.endUs
    }
    private fun attempt(action: () -> Unit) {
        if (busy || closing) return
        localError = null
        try { action() } catch (error: Exception) { localError = error.message ?: "操作未完成" }
        publish()
    }

    fun act(action: EditorAction) = attempt {
        val current = song ?: return@attempt
        when (action) {
            EditorAction.PLAY_PAUSE -> if (preview.state.value.playing) preview.pause() else {
                if (stage is EditorStage.AtSeam) stage = EditorStage.Ready
                preview.play()
            }
            EditorAction.RESUME -> { val position = editedPosition(); stage = EditorStage.Ready; edited(position, play = true) }
            EditorAction.BACKWARD -> preview.seek(preview.positionUs() - 5_000_000)
            EditorAction.FORWARD -> preview.seek(preview.positionUs() + 5_000_000)
            EditorAction.MARK_START -> {
                require(preview.state.value.mode == PreviewMode.EDITED && preview.state.value.ready) { "请先切回编辑结果试听" }
                stage = EditorStage.StartMarked(editedPosition()) // 不暂停、不重建播放列表。
            }
            EditorAction.MARK_END -> {
                val start = (stage as? EditorStage.StartMarked)?.startUs ?: error("请先标记开始")
                val end = editedPosition()
                preview.pause() // 即使范围无效也响应结束按钮暂停。
                require(end > start) { "结束时间必须晚于开始时间" }
                stage = EditorStage.RangeMarked(start, end)
            }
            EditorAction.CANCEL_MARK -> { val position = editedPosition(); preview.pause(); stage = EditorStage.Ready; edited(position) }
            EditorAction.PREVIEW_RANGE -> {
                val (start, end) = range()
                preview.load(SegmentTimeline.slice(current.segments, start, end), uris, PreviewMode.SELECTION,
                    baseUs = start, play = true, onEnded = { edited(end) })
            }
            EditorAction.PREVIEW_SEAM -> {
                val seam = (stage as? EditorStage.AtSeam)?.positionUs ?: editedPosition()
                val from = (seam - 2_000_000).coerceAtLeast(0)
                val to = (seam + 2_000_000).coerceAtMost(SegmentTimeline.duration(current.segments))
                require(to > from) { "没有可试听的片段" }
                preview.load(SegmentTimeline.slice(current.segments, from, to), uris, PreviewMode.SEAM,
                    baseUs = from, play = true, onEnded = { edited(seam) })
            }
            EditorAction.ORIGINAL -> {
                val sourceTime = SegmentTimeline.sourcePosition(current.segments, editedPosition()) ?: 0
                preview.load(listOf(AudioSegment(current.sourceId, 0, originalDurationUs)), uris, PreviewMode.ORIGINAL, sourceTime, play = true)
            }
            EditorAction.EDITED -> edited(editedPosition(), play = true)
            EditorAction.DELETE_RANGE, EditorAction.KEEP_RANGE -> {
                val (start, end) = range()
                val kept = if (action == EditorAction.DELETE_RANGE) SegmentTimeline.delete(current.segments, start, end)
                    else SegmentTimeline.slice(current.segments, start, end)
                val removed = if (action == EditorAction.DELETE_RANGE) SegmentTimeline.slice(current.segments, start, end)
                    else SegmentTimeline.slice(current.segments, 0, start) + SegmentTimeline.slice(current.segments, end, SegmentTimeline.duration(current.segments))
                val position = if (action == EditorAction.DELETE_RANGE) start.coerceAtMost(SegmentTimeline.duration(kept)) else 0
                edit(kept, current.deleted + if (removed.isEmpty()) emptyList() else listOf(DeletedAudio(UUID.randomUUID().toString(), removed)), position, EditorStage.AtSeam(position, if (action == EditorAction.DELETE_RANGE) "已删除选区" else "已保留选区"))
            }
            EditorAction.SPLIT -> { val position = editedPosition(); edit(SegmentTimeline.split(current.segments, position), current.deleted, position, EditorStage.Ready) }
            EditorAction.UNDO -> history(undo = true)
            EditorAction.REDO -> history(undo = false)
            EditorAction.RESTORE_ALL -> edit(listOf(AudioSegment(current.sourceId, 0, originalDurationUs)), emptyList(), 0, EditorStage.Ready)
        }
    }

    private fun version(current: SongEdit) = EditVersion(current.segments, current.deleted, editedPosition())
    private fun edit(parts: List<AudioSegment>, deleted: List<DeletedAudio>, positionUs: Long, nextStage: EditorStage) {
        val current = song ?: return
        if (parts == current.segments && deleted == current.deleted) return
        val next = current.copy(segments = parts, deleted = deleted, resumePositionUs = positionUs,
            undo = (current.undo + version(current)).takeLast(50), redo = emptyList())
        commit(next, nextStage)
    }
    private fun history(undo: Boolean) {
        val current = song ?: return
        val target = (if (undo) current.undo else current.redo).lastOrNull() ?: return
        val next = current.copy(segments = target.segments, deleted = target.deleted, resumePositionUs = target.positionUs,
            undo = if (undo) current.undo.dropLast(1) else (current.undo + version(current)).takeLast(50),
            redo = if (undo) (current.redo + version(current)).takeLast(50) else current.redo.dropLast(1))
        commit(next, EditorStage.Ready)
    }
    private fun commit(next: SongEdit, nextStage: EditorStage) {
        val previous = song ?: return
        busy = true; preview.pause(); publish()
        scope.launch {
            mutex.withLock {
                try {
                    repository.update { snapshot ->
                        require(snapshot.project.songs.firstOrNull { it.id == previous.id } == previous) { "歌曲已改变，请重新打开裁剪器" }
                        snapshot.copy(project = snapshot.project.copy(songs = snapshot.project.songs.map { if (it.id == next.id) next else it }))
                    }
                    song = next; stage = nextStage
                    waveform.updateParts(next.segments)
                    onProjectCommitted()
                    edited(next.resumePositionUs) // 删除/恢复/撤销后保持暂停。
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { localError = "编辑未保存：${error.message ?: "文件读写失败"}" }
                finally { busy = false; publish() }
            }
        }
    }

    fun restoreDeleted(id: String) = attempt {
        val current = song ?: return@attempt
        val removed = current.deleted.first { it.id == id }
        val parts = SegmentTimeline.restore(current.segments, removed.segments)
        val position = SegmentTimeline.editedPosition(parts, removed.segments.first().sourceStartUs)
        edit(parts, current.deleted.filterNot { it.id == id }, position, EditorStage.AtSeam(position, "已恢复删除内容"))
    }

    fun setBoundary(start: Boolean, valueUs: Long) = attempt {
        val current = song ?: return@attempt
        val total = SegmentTimeline.duration(current.segments)
        require(valueUs in 0..total) { "时间超出编辑结果时长" }
        preview.pause()
        val selected = stage as? EditorStage.RangeMarked
        val begun = stage as? EditorStage.StartMarked
        if (start) {
            if (selected != null) { require(valueUs < selected.endUs) { "起点必须早于终点" }; stage = selected.copy(startUs = valueUs) }
            else stage = EditorStage.StartMarked(valueUs)
        } else {
            val from = selected?.startUs ?: begun?.startUs ?: error("请先设置起点")
            require(valueUs > from) { "终点必须晚于起点" }
            stage = EditorStage.RangeMarked(from, valueUs)
        }
    }
    fun inputBoundary(start: Boolean, text: String) = attempt { setBoundary(start, TimeInput.parseUs(text)) }
    fun adjustBoundary(start: Boolean, deltaUs: Long) {
        val selected = stage as? EditorStage.RangeMarked
        val begun = stage as? EditorStage.StartMarked
        val value = if (start) selected?.startUs ?: begun?.startUs else selected?.endUs
        if (value == null) { localError = "请先设置相应时间"; publish() } else setBoundary(start, value + deltaUs)
    }
    fun boundaryAtPosition(start: Boolean) = setBoundary(start, editedPosition())
    fun inputSeek(text: String) = attempt { seek(TimeInput.parseUs(text)) }
    fun seek(positionUs: Long) = attempt {
        preview.pause()
        if (preview.state.value.mode == PreviewMode.ORIGINAL) preview.seek(positionUs.coerceIn(0, originalDurationUs))
        else edited(positionUs.coerceIn(0, song?.let { SegmentTimeline.duration(it.segments) } ?: 0))
    }

    private suspend fun saveCursor() {
        val current = song ?: return
        val position = editedPosition()
        val next = current.copy(resumePositionUs = position)
        repository.update { snapshot ->
            require(snapshot.project.songs.firstOrNull { it.id == current.id } == current) { "项目已改变" }
            snapshot.copy(project = snapshot.project.copy(songs = snapshot.project.songs.map { if (it.id == current.id) next else it }))
        }
        song = next
    }
    fun close(onClosed: () -> Unit) {
        if (closing) return
        closing = true; preview.pause(); publish(); ++epoch
        scope.launch { mutex.withLock {
            busy = true; publish()
            try { saveCursor(); waveform.clear(); song = null; requestedSongId = null; stage = EditorStage.Ready; localError = null; preview.clear(); onClosed() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { localError = "返回前保存失败：${error.message ?: "文件读写失败"}" }
            finally { busy = false; closing = false; publish() }
        } }
    }
    fun pauseForBackground() {
        preview.pause()
        if (song != null && !busy && !closing) scope.launch { mutex.withLock {
            busy = true; publish()
            try { saveCursor() } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { localError = "位置未保存：${error.message}" }
            finally { busy = false; publish() }
        } }
    }
    fun stopWorkspacePreview() { if (song == null) { localError = null; preview.clear(); publish() } }
    fun playWorkspace() {
        if (preview.state.value.mode == PreviewMode.WORKSPACE && preview.state.value.error == null) {
            if (preview.state.value.playing) preview.pause() else preview.play()
            return
        }
        if (busy || closing || song != null) return
        scope.launch { mutex.withLock {
            busy = true; publish()
            try {
                localError = null
                preview.clear()
                val parts = repository.snapshot.value.project.songs.flatMap { it.segments }
                uris = resolveUris(parts.map { it.sourceId }.toSet())
                require(parts.isNotEmpty()) { "没有可试听的歌曲片段" }
                preview.load(parts, uris, PreviewMode.WORKSPACE, play = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { localError = error.message ?: "无法试听" }
            finally { busy = false; publish() }
        } }
    }
    fun release() { waveform.release(); preview.release() }
}
