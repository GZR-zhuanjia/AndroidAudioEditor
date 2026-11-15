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

data class SaveLocation(val uri: String, val name: String, val persistent: Boolean)
data class SharePayload(val token: String, val uri: String, val name: String)
data class SavedCopy(val name: String, val location: String)
data class FileCopyProgress(val label: String, val fraction: Float)

/** Only creates NEW documents in a user-authorized tree. Original results are always read-only. */
class ResultFileService(
    private val context: Context, private val repository: FileWorkspaceRepository,
    private val grants: UriGrantLedger, private val warn: (String) -> Unit,
) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("external_save", Context.MODE_PRIVATE)
    private val shares = File(context.cacheDir, "shared_audio")

    suspend fun lastLocation(): SaveLocation? = withContext(Dispatchers.IO) {
        val uri = preferences.getString("tree", null) ?: return@withContext null
        val persistent = resolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isReadPermission && it.isWritePermission }
        SaveLocation(uri, preferences.getString("name", "上次位置").orEmpty(), persistent)
    }

    private fun folder(tree: Uri): Pair<Uri, String> {
        require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree)) { "请选择系统文件选择器中的文件夹" }
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            check(cursor.moveToFirst()) { "保存位置不可用" }
            require(cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR &&
                (cursor.getInt(2) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0) { "该位置不允许创建文件，请重新选择可写文件夹" }
            return document to (cursor.getString(0) ?: "所选文件夹")
        }
        error("保存位置不可用，若使用U盘请确认已连接，或重新授权该位置")
    }

    suspend fun rememberLocation(uri: Uri, flags: Int): SaveLocation = withContext(Dispatchers.IO) {
        val (_, name) = folder(uri)
        val persistent = grants.takeTree(uri, flags)
        check(preferences.edit().putString("tree", uri.toString()).putString("name", name).commit()) { "保存位置未能记住，请重试" }
        SaveLocation(uri.toString(), name, persistent)
    }

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

    suspend fun saveCopy(
        id: String, location: SaveLocation, report: (FileCopyProgress) -> Unit, onSaved: (SavedCopy) -> Unit,
    ) = withContext(Dispatchers.IO) { withResult(id) { access -> coroutineScope {
        val signal = CancellationSignal()
        val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { signal.cancel() }
        }
        var ownedDocument: Uri? = null
        var finished = false
        try {
            val tree = Uri.parse(location.uri)
            val (parent, title) = folder(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val ids = mutableSetOf<String>(); val names = mutableListOf<String>()
            val cursor = resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null, signal) ?: error("无法检查目标目录，不会尝试覆盖文件")
            cursor.use {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    ids += requireNotNull(it.getString(0)); names += it.getString(1).orEmpty()
                    require(ids.size <= 100000) { "目标目录文件过多，请选择其他文件夹" }
                }
            }
            val desired = ResultNames.unique(access.record.displayName, names)
            currentCoroutineContext().ensureActive()
            val created = DocumentsContract.createDocument(resolver, parent, "audio/mpeg", desired) ?: error("无法在所选位置创建文件")
            // A provider returning an old ID must never be opened for writing or deleted on failure.
            require(created.authority == tree.authority && DocumentsContract.getDocumentId(created) !in ids) { "文件提供者返回了已有文件，已停止以避免覆盖" }
            ownedDocument = created
            val digest = MessageDigest.getInstance("SHA-256"); val reporter = Reporter(report)
            val size = access.record.sizeBytes; var copied = 0L
            reporter.send("正在复制到 $title", 0f, true)
            val descriptor = resolver.openFileDescriptor(created, "w", signal) ?: error("无法打开新建副本")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output -> FileInputStream(access.file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer); if (read < 0) break
                    copied += read; require(copied <= size) { "内部成果大小已改变" }
                    output.write(buffer, 0, read); digest.update(buffer, 0, read)
                    reporter.send("正在复制到 $title", copied.toFloat() / size * 0.5f)
                }
                output.flush()
                if (OsConstants.S_ISREG(Os.fstat(descriptor.fileDescriptor).st_mode)) output.fd.sync()
            } }
            require(copied == size) { "未完整读取内部成果" }
            reporter.send("正在核对外部副本", 0.5f, true)
            val readback = resolver.openFileDescriptor(created, "r", signal) ?: error("无法核对新副本")
            val verified = MessageDigest.getInstance("SHA-256"); var checked = 0L
            ParcelFileDescriptor.AutoCloseInputStream(readback).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer); if (read < 0) break
                    checked += read; require(checked <= size) { "外部副本大小不符" }
                    verified.update(buffer, 0, read); reporter.send("正在核对外部副本", 0.5f + checked.toFloat() / size * 0.5f)
                }
            }
            require(checked == size && digest.digest().contentEquals(verified.digest())) { "外部副本内容核对失败" }
            val actualName = resolver.query(created, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: desired
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) { finished = true; onSaved(SavedCopy(actualName, title)) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { throw IOException("另存失败：保存位置不可用或读写失败。若使用U盘，请确认已连接，或重新选择位置。内部成果仍保留。${error.message.orEmpty()}", error) }
        finally {
            cancellation.cancel()
            if (!finished && ownedDocument != null) withContext(NonCancellable + Dispatchers.IO) {
                val removed = runCatching { DocumentsContract.deleteDocument(resolver, requireNotNull(ownedDocument)) }.getOrDefault(false)
                if (!removed) warn("所选位置可能留下未完成副本，请重新连接后检查；内部成果没有删除。")
            }
        }
    } } }
}
