package com.audioassistant.waveform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.audioassistant.engine.JniNativeAudioEngine
import com.audioassistant.engine.NativeProbe
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class WaveformResult(val info: NativeProbe, val data: WaveformData, val engineVersions: String)

/** Only derived data in cacheDir. No source/result deletion, source copying or result registration. */
class WaveformRepository(context: Context) : Closeable {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.cacheDir, "waveforms-v1")
    private val engine = JniNativeAudioEngine(context)

    private fun key(input: Uri): String? {
        val modified: Long
        val size: Long
        if (input.scheme == "file") {
            val file = File(requireNotNull(input.path))
            modified = file.lastModified(); size = file.length()
        } else {
            val metadata = runCatching {
                resolver.query(input, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use {
                    if (it.moveToFirst() && !it.isNull(0) && !it.isNull(1)) it.getLong(0) to it.getLong(1) else null
                }
            }.getOrNull() ?: return null
            modified = metadata.first; size = metadata.second
        }
        if (modified <= 0 || size <= 0) return null // unknown provider stamp: reanalyze on reopening
        return MessageDigest.getInstance("SHA-256").digest("ff7.1.5-wave1|$input|$modified|$size".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    suspend fun load(input: Uri, progress: (Long, Long) -> Unit): WaveformResult = withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建波形缓存目录" }
        // Previous process crashes may leave .part files; never scan source/result storage.
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles { file -> file.name.matches(Regex("[0-9a-f-]{36}\\.part")) && file.lastModified() < cutoff }
            ?.forEach { it.delete() }
        val cache = key(input)?.let { File(directory, "$it.wave") }
        val info = engine.probe(input)
        val versions = engine.versions()
        if (cache?.isFile == true) {
            try {
                val data = WaveformData.read(cache)
                cache.setLastModified(System.currentTimeMillis())
                return@withContext WaveformResult(info, data, versions)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { cache.delete() }
        }
        val temporary = File(directory, "${UUID.randomUUID()}.part")
        try {
            engine.analyze(input, temporary, progress)
            coroutineContext.ensureActive()
            val data = WaveformData.read(temporary)
            coroutineContext.ensureActive()
            if (cache != null) check(temporary.renameTo(cache)) { "波形缓存提交失败" }
            // 128 MiB LRU; never touch source/result directories or another live .part.
            val files = directory.listFiles { file -> file.name.matches(Regex("[0-9a-f]{64}\\.wave")) }.orEmpty().sortedBy { it.lastModified() }
            var bytes = files.sumOf { it.length() }
            for (file in files) if (bytes > 128L * 1024 * 1024 && file != cache) {
                val length = file.length(); if (file.delete()) bytes -= length
            }
            WaveformResult(info, data, versions)
        } finally {
            // Native may still be unwinding after cancellation. Unlinking the open temp is safe.
            withContext(NonCancellable + Dispatchers.IO) { temporary.delete() }
        }
    }
    override fun close() = engine.close()
}
