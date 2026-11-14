package com.audioassistant.waveform

import android.content.Context
import android.net.Uri
import com.audioassistant.model.AudioSegment
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WaveformPhase { EMPTY, LOADING, READY, CANCELLED, ERROR }
data class WaveformState(
    val songId: String? = null, val phase: WaveformPhase = WaveformPhase.EMPTY,
    val processedUs: Long = 0, val totalUs: Long = -1,
    val result: WaveformResult? = null, val parts: List<AudioSegment> = emptyList(), val error: String? = null,
)

/** Its flow is separate from the 100 ms playback clock. No audio analysis on the main thread. */
class WaveformSession(context: Context, private val scope: CoroutineScope) {
    private val repository = WaveformRepository(context)
    private val mutable = MutableStateFlow(WaveformState())
    val state = mutable.asStateFlow()
    private val generation = AtomicLong()
    private var job: Job? = null
    private var input: Uri? = null

    fun open(songId: String, uri: Uri, parts: List<AudioSegment>) {
        job?.cancel()
        input = uri
        val token = generation.incrementAndGet()
        mutable.value = WaveformState(songId = songId, phase = WaveformPhase.LOADING, parts = parts)
        job = scope.launch {
            try {
                val result = repository.load(uri) { processed, total ->
                    mutable.update { if (generation.get() == token) it.copy(processedUs = processed, totalUs = total) else it }
                }
                if (generation.get() == token) mutable.update { it.copy(phase = WaveformPhase.READY, result = result) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation.get() == token) mutable.update { it.copy(phase = WaveformPhase.ERROR, error = error.message ?: "波形分析失败") }
            }
        }
    }
    fun updateParts(parts: List<AudioSegment>) { mutable.update { it.copy(parts = parts) } }
    fun retry() {
        val current = mutable.value
        val uri = input ?: return
        val id = current.songId ?: return
        open(id, uri, current.parts)
    }
    fun cancel() {
        generation.incrementAndGet(); job?.cancel(); job = null
        mutable.update { it.copy(phase = WaveformPhase.CANCELLED, result = null, error = null) }
    }
    fun clear() { cancel(); input = null; mutable.value = WaveformState() }
    fun release() { clear(); repository.close() }
}
