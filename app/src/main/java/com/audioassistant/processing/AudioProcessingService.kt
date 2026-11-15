package com.audioassistant.processing

import android.content.Context
import android.net.Uri
import com.audioassistant.data.FileWorkspaceRepository
import com.audioassistant.engine.JniNativeAudioEngine
import com.audioassistant.model.EditProject
import com.audioassistant.model.LibraryRecord
import com.audioassistant.model.ResultOrigin
import com.audioassistant.model.SourceStorage
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ProcessingProgress(val label: String, val fraction: Float?)

/** Finite in-process tasks. No WorkManager, network, task restart or simulated media. */
class AudioProcessingService(
    context: Context, private val repository: FileWorkspaceRepository, private val onCleanupWarning: (String) -> Unit,
) : Closeable {
    private val filesDir = context.filesDir
    private val engine = JniNativeAudioEngine(context)

    private fun progress(position: Long, total: Long): Float? =
        if (total > 0) (position.toDouble() / total).toFloat().coerceIn(0f, 1f) else null

    private fun requireSpace(bytes: Long) {
        require(bytes > 0 && filesDir.usableSpace >= bytes) { "内部存储空间不足，请释放空间后重试；已有成果会保留" }
    }

    private suspend fun <T> temporary(block: suspend (File, File) -> T): T = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val directory = File(filesDir, "processing/$id")
        val output = repository.internalFile("results/$id.part")
        check(directory.mkdirs()) { "无法建立音频处理工作目录" }
        try {
            check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs()) { "无法建立成果目录" }
            requireSpace(16L * 1024 * 1024)
            block(directory, output)
        } finally {
            // Native cancellation may still be unwinding. Unlinking app-owned open files is safe on Android.
            withContext(NonCancellable + Dispatchers.IO) {
                val outputClean = !output.exists() || output.delete()
                val directoryClean = !directory.exists() || directory.deleteRecursively()
                if (!outputClean || !directoryClean) onCleanupWarning("部分音频临时文件未能清理，将在下次正确读取项目后重试；已登记的成果不受影响。")
            }
        }
    }

    private suspend fun register(
        temporary: File, name: String, origin: ResultOrigin, onRegistered: (LibraryRecord) -> Unit,
    ): LibraryRecord {
        coroutineContext.ensureActive()
        require(temporary.isFile && temporary.length() > 0) { "处理没有生成有效音频文件" }
        val info = engine.probe(Uri.fromFile(temporary))
        require(info.codecName == "mp3" && info.durationUs > 0 && info.channelCount in 1..2 && info.sampleRateHz > 0) { "输出 MP3 验证失败，未登记成果" }
        val size = temporary.length()
        RandomAccessFile(temporary, "rw").use { it.fd.sync() }
        coroutineContext.ensureActive()
        // Cancellation before this point cannot register. Once committing, complete the record + callback atomically.
        return withContext(NonCancellable) {
            val id = UUID.randomUUID().toString()
            val path = "results/$id.mp3"
            val destination = repository.internalFile(path)
            check(!destination.exists() && temporary.renameTo(destination)) { "成果文件提交失败" }
            var registered: LibraryRecord? = null
            try {
                repository.update { snapshot ->
                    val base = name.trim().replace(Regex("(?i)\\.mp3$"), "").replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").take(100).trim()
                    require(base.isNotBlank()) { "请输入有效文件名" }
                    val existing = snapshot.results.map { it.displayName.lowercase() }.toSet()
                    var display = "$base.mp3"; var copy = 2
                    while (display.lowercase() in existing) { display = "$base (${copy++}).mp3" }
                    val result = LibraryRecord(id, display, path, info.durationUs, size, origin)
                    registered = result
                    snapshot.copy(results = snapshot.results + result)
                }
            } catch (error: Exception) {
                if (!repository.isReferenced(path) && destination.exists() && !destination.delete())
                    onCleanupWarning("未登记的输出文件暂未能删除，将在下次正确读取项目后清理；没有创建成果记录。")
                throw error
            }
            requireNotNull(registered).also(onRegistered)
        }
    }

    suspend fun convert(
        input: Uri, name: String, report: (ProcessingProgress) -> Unit, onRegistered: (LibraryRecord) -> Unit,
    ): LibraryRecord = temporary { _, output ->
        report(ProcessingProgress("正在读取并转换音轨", null))
        val copied = engine.convertMp3(input, output) { position, total ->
            report(ProcessingProgress("正在处理音轨", progress(position, total)?.times(0.94f)))
        }
        report(ProcessingProgress(if (copied) "音轨已直接复制，正在验证" else "LAME 编码完成，正在验证", 0.96f))
        register(output, name, ResultOrigin.VIDEO, onRegistered)
    }

    suspend fun export(
        project: EditProject, name: String, normalize: Boolean,
        report: (ProcessingProgress) -> Unit, onRegistered: (LibraryRecord) -> Unit,
    ): LibraryRecord = temporary { directory, output ->
        require(project.songs.isNotEmpty() && project.songs.all { it.segments.isNotEmpty() }) { "每首歌曲至少需要一段保留音频，请移除空歌曲后导出" }
        require(project.songs.size <= 4096) { "一次最多拼接4096首歌曲" }
        val durations = project.songs.map { song -> song.segments.sumOf { it.durationUs } }
        val total = durations.fold(0L) { sum, duration -> Math.addExact(sum, duration) }
        require(total <= 24L * 60 * 60 * 1_000_000) { "拼接总时长最多24小时" }
        // float32 stereo 48 kHz; retain all final PCM, plus one song while normalizing, plus MP3/headroom.
        val pcmUs = Math.addExact(total, if (normalize) durations.maxOrNull() ?: 0 else 0)
        requireSpace(Math.addExact(Math.multiplyExact(pcmUs, 384_000L) / 1_000_000 + total / 1_000_000 * 40_000, 32L * 1024 * 1024))
        val byId = project.sources.associateBy { it.id }
        val internal = project.sources.filter { it.storage == SourceStorage.INTERNAL_FILE }.map { it.location }.toSet()
        repository.withFileReferences(internal) {
            val rendered = mutableListOf<File>()
            project.songs.forEachIndexed { index, song ->
                coroutineContext.ensureActive()
                val source = byId.getValue(song.sourceId)
                val uri = if (source.storage == SourceStorage.PERSISTED_URI) Uri.parse(source.location)
                    else Uri.fromFile(repository.internalFile(source.location))
                val raw = File(directory, "$index.raw")
                val base = index.toFloat() / project.songs.size * 0.7f
                val weight = 0.7f / project.songs.size
                report(ProcessingProgress("正在裁剪第${index + 1}/${project.songs.size}首", base))
                val frames = engine.renderSong(uri, song.segments, raw) { position, length ->
                    report(ProcessingProgress("正在裁剪第${index + 1}/${project.songs.size}首", progress(position, length)?.let { base + it * weight * (if (normalize) 0.4f else 1f) }))
                }
                require(frames > 0 && raw.length() == Math.multiplyExact(frames, 8L)) { "剪辑 PCM 输出不完整" }
                if (normalize) {
                    val adjusted = File(directory, "$index.normalized.raw")
                    val mode = try {
                        engine.normalizeSong(raw, adjusted) { position, length ->
                            report(ProcessingProgress("第${index + 1}首响度分析与处理", progress(position, length)?.let { base + weight * (0.4f + it * 0.6f) }))
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { throw IllegalStateException("第${index + 1}首响度处理失败：${error.message}。可关闭响度均衡后重试", error) }
                    require(mode in setOf("linear", "dynamic", "silence") && adjusted.isFile && adjusted.length() == raw.length()) { "响度处理结果不完整，未导出" }
                    check(raw.delete()) { "临时文件无法清理，已停止以保护存储空间" }
                    rendered += adjusted
                } else rendered += raw
            }
            report(ProcessingProgress("正在连续编码 MP3", 0.7f))
            engine.encodeMp3(rendered, output) { position, length ->
                report(ProcessingProgress("正在连续编码 MP3", progress(position, length)?.let { 0.7f + it * 0.24f }))
            }
            report(ProcessingProgress("正在验证并保存成果", 0.96f))
            register(output, name, ResultOrigin.MERGE, onRegistered)
        }
    }
    override fun close() = engine.close()
}
