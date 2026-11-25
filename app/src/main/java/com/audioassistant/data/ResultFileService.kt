package com.audioassistant.data

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SharePayload(val token: String, val uri: String, val name: String)
data class FileCopyProgress(val label: String, val fraction: Float)

/** Shares read-only snapshots and copies results to documents created by the system picker. */
class ResultFileService(
    private val context: Context, private val repository: FileWorkspaceRepository,
    private val warn: (String) -> Unit,
) {
    private val resolver = context.contentResolver
    private val shares = File(context.cacheDir, "shared_audio")

    private class Reporter(private val callback: (FileCopyProgress) -> Unit) {
        private var last = 0L
        fun send(label: String, fraction: Float, force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (force || now - last >= 100) { last = now; callback(FileCopyProgress(label, fraction.coerceIn(0f, 1f))) }
        }
    }

    private suspend fun <T> withResult(id: String, block: suspend (ResultAccess) -> T): T {
        val access = repository.openResult(id)
        try { return block(access) }
        finally {
            access.close()
            withContext(NonCancellable + Dispatchers.IO) {
                if (!repository.cleanupResults()) warn("部分无引用成果文件暂未清理，将在稍后重试")
            }
        }
    }

    suspend fun cleanupShares(startup: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        var complete = true
        shares.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}")) }?.forEach { directory ->
            if (directory.lastModified() < cutoff) { if (!directory.deleteRecursively()) complete = false }
            else if (startup) directory.listFiles()?.filter { it.name.matches(Regex("[0-9a-f-]{36}\\.part")) }
                ?.forEach { if (!it.delete()) complete = false }
        }
        complete
    }

    suspend fun prepareShare(id: String, report: (FileCopyProgress) -> Unit): SharePayload = withContext(Dispatchers.IO) {
        withResult(id) { access ->
            if (!cleanupShares()) warn("部分过期分享副本暂未清理")
            val record = access.record
            val hash = MessageDigest.getInstance("SHA-256").digest("${record.id}|${record.displayName}|${record.sizeBytes}|${access.file.lastModified()}".toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val directory = File(shares, hash)
            check(directory.isDirectory || directory.mkdirs()) { "无法创建分享副本目录" }
            directory.setLastModified(System.currentTimeMillis())
            val completed = File(directory, "${ResultNames.base(record.displayName)}.mp3")
            val staging = File(directory, "${UUID.randomUUID()}.part")
            try {
                if (!(completed.isFile && completed.length() == record.sizeBytes)) {
                    require(shares.usableSpace > record.sizeBytes + 16L * 1024 * 1024) { "内部空间不足，无法创建分享副本" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val reporter = Reporter(report)
                    var count = 0L
                    FileInputStream(access.file).use { input -> FileOutputStream(staging).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer); if (read < 0) break
                            output.write(buffer, 0, read); digest.update(buffer, 0, read); count += read
                            require(count <= record.sizeBytes) { "原成果大小已变化" }
                            reporter.send("正在准备分享副本", count.toFloat() / record.sizeBytes * 0.5f)
                        }
                        output.fd.sync()
                    } }
                    require(count == record.sizeBytes && staging.length() == count) { "分享副本未完整写入" }
                    val verified = hashStream(staging, record.sizeBytes, reporter, "正在核对分享副本")
                    require(digest.digest().contentEquals(verified)) { "分享副本内容核对失败" }
                    currentCoroutineContext().ensureActive()
                    check(staging.renameTo(completed)) { "无法提交分享副本" }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", completed)
                SharePayload(UUID.randomUUID().toString(), uri.toString(), completed.name)
            } finally { if (staging.exists() && !staging.delete()) warn("分享临时副本未能清理，将在过期后重试") }
        }
    }

    private suspend fun hashStream(file: File, size: Long, reporter: Reporter, label: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer); if (read < 0) break
                count += read; require(count <= size) { "复制文件大小不符" }
                digest.update(buffer, 0, read); reporter.send(label, 0.5f + count.toFloat() / size * 0.5f)
            }
        }
        require(count == size) { "复制文件被截断" }
        return digest.digest()
    }

    /** The destination is the new document returned by ACTION_CREATE_DOCUMENT. */
    suspend fun saveCopy(
        id: String, destination: Uri, report: (FileCopyProgress) -> Unit, onSaved: (String) -> Unit,
    ) {
        require(destination.scheme == "content" && DocumentsContract.isDocumentUri(context, destination)) {
            "系统未返回有效的保存位置。"
        }
        var finished = false
        try {
            withContext(Dispatchers.IO) {
                withResult(id) { access ->
                    coroutineScope {
                        val signal = CancellationSignal()
                        val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { signal.cancel() }
                        }
                        try {
                            val name = copyAndVerify(access, destination, signal, Reporter(report))
                            currentCoroutineContext().ensureActive()
                            withContext(NonCancellable) {
                                finished = true
                                onSaved(name)
                            }
                        } finally {
                            cancellation.cancel()
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IOException("另存失败。请重试并选定可写位置，使用 U 盘时请确认已连接。内部成果仍保留。\n" +
                error.message.orEmpty(), error)
        } finally {
            if (!finished) discardCopy(destination)
        }
    }

    /** Only for an unfinished document returned by this save request's system picker. */
    suspend fun discardCopy(destination: Uri) = withContext(NonCancellable + Dispatchers.IO) {
        val removed = runCatching {
            destination.scheme == "content" && DocumentsContract.isDocumentUri(context, destination) &&
                DocumentsContract.deleteDocument(resolver, destination)
        }.getOrDefault(false)
        if (!removed) warn("所选位置可能留下未完成副本，请检查外部文件。内部成果仍保留。")
    }

    private suspend fun copyAndVerify(
        access: ResultAccess, destination: Uri, signal: CancellationSignal, reporter: Reporter,
    ): String {
        val size = access.record.sizeBytes
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        reporter.send("正在复制音频", 0f, true)
        val descriptor = resolver.openFileDescriptor(destination, "w", signal) ?: error("无法打开新建副本")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
            FileInputStream(access.file).use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(copied <= size) { "内部成果大小已改变" }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    reporter.send("正在复制音频", copied.toFloat() / size * 0.5f)
                }
            }
            output.flush()
            if (OsConstants.S_ISREG(Os.fstat(descriptor.fileDescriptor).st_mode)) output.fd.sync()
        }
        require(copied == size) { "未完整读取内部成果" }

        reporter.send("正在核对外部副本", 0.5f, true)
        val readback = resolver.openFileDescriptor(destination, "r", signal) ?: error("无法核对新副本")
        val verified = MessageDigest.getInstance("SHA-256")
        var checked = 0L
        ParcelFileDescriptor.AutoCloseInputStream(readback).use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                checked += read
                require(checked <= size) { "外部副本大小不符" }
                verified.update(buffer, 0, read)
                reporter.send("正在核对外部副本", 0.5f + checked.toFloat() / size * 0.5f)
            }
        }
        require(checked == size && digest.digest().contentEquals(verified.digest())) { "外部副本内容核对失败" }

        return runCatching {
            resolver.query(destination, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null, signal)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: "音频副本"
    }
}
