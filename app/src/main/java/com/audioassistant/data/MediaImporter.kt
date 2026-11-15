package com.audioassistant.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.audioassistant.model.SourceReference
import com.audioassistant.model.SourceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** 系统元数据探测；不解码、不转换，不声称替代阶段 5 的 FFmpeg 探测。 */
data class ImportedMedia(val uri: String, val name: String, val durationUs: Long?, val sizeBytes: Long?, val mimeType: String?)
data class PreparedSource(val source: SourceReference)

class MediaImporter(private val context: Context, private val repository: FileWorkspaceRepository, private val grants: UriGrantLedger) {
    private val resolver = context.contentResolver

    suspend fun inspect(uri: Uri, video: Boolean): ImportedMedia = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "请选择系统文件选择器提供的文件" }
        var name = "未命名${if (video) "视频" else "音频"}"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
            }
        }
        val extractor = MediaExtractor()
        var found = false
        var duration: Long? = null
        try {
            extractor.setDataSource(context, uri, null)
            repeat(extractor.trackCount) { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith(if (video) "video/" else "audio/")) {
                    found = true
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        val candidate = format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0 }
                        if (candidate != null) duration = maxOf(duration ?: 0, candidate)
                    }
                }
            }
        } finally { extractor.release() }
        require(found) { if (video) "没有找到视频轨道，或系统暂不支持此文件" else "没有找到音频轨道，或系统暂不支持此文件" }
        if (duration == null) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?.takeIf { it > 0 && it <= Long.MAX_VALUE / 1000 }?.times(1000)
            } catch (_: Exception) { /* 视频可保留未知时长；歌曲创建片段前必须有真实时长。 */ }
            finally { retriever.release() }
        }
        if (!video) require(duration != null) { "无法读取歌曲时长，暂不能加入剪辑" }
        ImportedMedia(uri.normalizeScheme().toString(), name, duration, size, resolver.getType(uri))
    }

    suspend fun prepareSong(media: ImportedMedia): PreparedSource = withContext(Dispatchers.IO) {
        val uri = Uri.parse(media.uri)
        val hadGrant = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        val retained = hadGrant || grants.takeRead(uri)
        val id = UUID.randomUUID().toString()
        val location = if (retained) media.uri else copySource(uri, id, media.sizeBytes)
        PreparedSource(
            SourceReference(id, media.name, if (retained) SourceStorage.PERSISTED_URI else SourceStorage.INTERNAL_FILE,
                location, requireNotNull(media.durationUs), media.uri, media.mimeType, media.sizeBytes),
        )
    }

    private suspend fun copySource(uri: Uri, id: String, knownSize: Long?): String {
        val folder = File(context.filesDir, "sources")
        check(folder.isDirectory || folder.mkdirs()) { "无法创建内部文件目录" }
        val temporary = File(folder, "$id.part")
        val final = File(folder, "$id.source")
        val reserve = 8L * 1024 * 1024
        require(folder.usableSpace > reserve && (knownSize == null || knownSize < folder.usableSpace - reserve)) { "内部存储空间不足" }
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法打开源文件" }
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(folder.usableSpace > reserve + count) { "内部存储空间不足" }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            check(temporary.length() > 0 && (knownSize == null || temporary.length() == knownSize)) { "源文件未完整复制，请重新选择" }
            check(temporary.renameTo(final)) { "无法保存内部副本" }
            return "sources/${final.name}"
        } catch (error: Throwable) {
            temporary.delete()
            final.delete()
            throw error
        }
    }

    suspend fun available(source: SourceReference): Boolean = withContext(Dispatchers.IO) {
        try {
            when (source.storage) {
                SourceStorage.INTERNAL_FILE -> repository.internalFile(source.location).let { it.isFile && it.length() > 0 }
                SourceStorage.PERSISTED_URI -> resolver.openAssetFileDescriptor(Uri.parse(source.location), "r")?.use { true } ?: false
            }
        } catch (_: Exception) { false }
    }

    /** 回滚只清无引用内部副本；持久URI授权由写入队列末的账本统一回收。 */
    suspend fun discard(prepared: PreparedSource) = withContext(Dispatchers.IO) {
        val source = prepared.source
        val referenced = repository.snapshot.value.project.sources.any {
            it.storage == source.storage && it.location == source.location
        }
        if (!referenced) {
            when (source.storage) {
                SourceStorage.INTERNAL_FILE -> if (!repository.isReferenced(source.location)) {
                    repository.internalFile(source.location).delete()
                }
                SourceStorage.PERSISTED_URI -> Unit
            }
        }
    }
}
