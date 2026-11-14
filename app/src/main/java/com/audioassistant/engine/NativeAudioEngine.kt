package com.audioassistant.engine

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface NativeAudioEngine : Closeable {
    suspend fun probe(input: Uri): NativeProbe
    suspend fun analyze(input: Uri, output: File, onProgress: (Long, Long) -> Unit)
    suspend fun versions(): String
}

/** -1 表示容器未给出时长；不会用猜测值冒充探测结果。 */
@Keep
data class NativeProbe(
    val durationUs: Long, val codecName: String, val sampleRateHz: Int,
    val channelCount: Int, val streamIndex: Int, val streamCount: Int,
)

@Keep
fun interface NativeProgress { fun onProgress(positionUs: Long, totalUs: Long) }

@Keep
internal object NativeBridge {
    external fun createTask(): Long
    external fun cancelTask(id: Long)
    external fun releaseTask(id: Long)
    external fun probe(id: Long, fd: Int, offset: Long, size: Long): NativeProbe
    external fun analyze(id: Long, fd: Int, offset: Long, size: Long, output: String, progress: NativeProgress)
    external fun versions(): String
}

/** 单个原生工作线程；取消回调直接设置 native atomic 标记，不等待阻塞 JNI 返回。 */
class JniNativeAudioEngine(context: Context) : NativeAudioEngine {
    private val resolver = context.applicationContext.contentResolver
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "audio-native") }
    private val active = AtomicLong(0)
    @Volatile private var closed = false
    private val library by lazy { runCatching { System.loadLibrary("audioengine") } }

    private suspend fun <T> task(block: (Long) -> T): T = suspendCancellableCoroutine { continuation ->
        val token = AtomicLong(0)
        continuation.invokeOnCancellation { token.get().takeIf { it != 0L }?.let(NativeBridge::cancelTask) }
        try {
            executor.execute {
                if (!continuation.isActive) return@execute
                var id = 0L
                try {
                    check(!closed) { "音频引擎已关闭" }
                    library.getOrElse { throw IOException("原生音频库不可用，请先按本地构建说明编译 FFmpeg、LAME 和 JNI", it) }
                    id = NativeBridge.createTask()
                    check(id != 0L) { "无法建立原生任务" }
                    token.set(id); active.set(id)
                    if (!continuation.isActive || closed) NativeBridge.cancelTask(id)
                    val result = block(id)
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } catch (error: LinkageError) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("原生库版本或依赖不匹配", error))
                } finally {
                    if (id != 0L) { NativeBridge.releaseTask(id); active.compareAndSet(id, 0) }
                    token.set(0)
                }
            }
        } catch (error: RejectedExecutionException) { continuation.resumeWithException(IOException("音频引擎已关闭", error)) }
    }

    override suspend fun probe(input: Uri): NativeProbe = task { id ->
        (resolver.openAssetFileDescriptor(input, "r") ?: throw IOException("无法打开已授权音频")).use { file ->
            NativeBridge.probe(id, file.parcelFileDescriptor.fd, file.startOffset, file.declaredLength)
        }
    }
    override suspend fun analyze(input: Uri, output: File, onProgress: (Long, Long) -> Unit) = task { id ->
        (resolver.openAssetFileDescriptor(input, "r") ?: throw IOException("无法打开已授权音频")).use { file ->
            NativeBridge.analyze(id, file.parcelFileDescriptor.fd, file.startOffset, file.declaredLength,
                output.absolutePath, NativeProgress(onProgress))
        }
    }
    override suspend fun versions(): String = task { NativeBridge.versions() }
    override fun close() {
        closed = true
        active.get().takeIf { it != 0L }?.let(NativeBridge::cancelTask)
        executor.shutdown()
    }
}
