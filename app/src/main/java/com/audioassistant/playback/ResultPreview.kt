package com.audioassistant.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.audioassistant.data.FileWorkspaceRepository
import com.audioassistant.data.ResultAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResultPlaybackState(
    val id: String? = null, val name: String = "", val playing: Boolean = false, val ready: Boolean = false,
    val loading: Boolean = false, val positionUs: Long = 0, val durationUs: Long = 0, val error: String? = null,
)
data class ResultPlaybackSummary(val id: String? = null, val playing: Boolean = false, val loading: Boolean = false, val error: String? = null)

/** Plays the COMPLETE stored MP3, including its actual loudnorm result; no source preview substitution. */
@androidx.annotation.OptIn(UnstableApi::class)
class ResultPreview(
    private val context: Context, private val repository: FileWorkspaceRepository,
    private val scope: CoroutineScope, private val warn: (String) -> Unit,
) {
    private val mutable = MutableStateFlow(ResultPlaybackState())
    val state = mutable.asStateFlow()
    val summary = state.map { ResultPlaybackSummary(it.id, it.playing, it.loading, it.error) }
        .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, ResultPlaybackSummary())
    private var player: ExoPlayer? = null
    private var access: ResultAccess? = null
    private var opening: Job? = null
    private var generation = 0L
    private var wantedPlayback = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { publish(); if (player?.isPlaying == true) handler.postDelayed(this, 250) }
    }

    private fun engine(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { engine ->
        player = engine
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        engine.setHandleAudioBecomingNoisy(true)
        engine.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish(); handler.removeCallbacks(ticker)
                if (isPlaying) handler.post(ticker)
            }
            override fun onPlaybackStateChanged(playbackState: Int) { publish() }
            override fun onPlayerError(error: PlaybackException) {
                mutable.value = mutable.value.copy(loading = false, playing = false, ready = false,
                    error = "成果试听失败：文件或解码不可用（${error.errorCodeName}）")
            }
        })
    }

    fun toggle(id: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val current = player
        if (mutable.value.id == id && access != null && current != null && current.playerError == null) {
            wantedPlayback = !current.playWhenReady
            if (!wantedPlayback) current.pause() else {
                if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
                current.play()
            }
            publish(); return
        }
        stop()
        wantedPlayback = true
        val token = ++generation
        mutable.value = ResultPlaybackState(id = id, name = repository.snapshot.value.results.firstOrNull { it.id == id }?.displayName.orEmpty(), loading = true)
        opening = scope.launch {
            var acquired: ResultAccess? = null
            try {
                acquired = repository.openResult(id)
                if (token != generation) { acquired.close(); return@launch }
                access = acquired
                mutable.value = ResultPlaybackState(id, acquired.record.displayName, loading = true, durationUs = acquired.record.durationUs)
                engine().setMediaItem(MediaItem.Builder().setMediaId(id).setUri(Uri.fromFile(acquired.file)).build())
                engine().prepare(); engine().playWhenReady = wantedPlayback
            } catch (cancelled: CancellationException) { acquired?.close(); throw cancelled }
            catch (error: Exception) {
                acquired?.close()
                if (token == generation) {
                    player?.stop(); player?.clearMediaItems(); access = null
                    mutable.value = mutable.value.copy(loading = false, error = "无法试听：${error.message}")
                }
            }
        }
    }

    private fun publish() {
        val engine = player ?: return
        if (mutable.value.id == null) return
        val duration = engine.duration.takeIf { it != C.TIME_UNSET && it > 0 && it < Long.MAX_VALUE / 1000 }?.times(1000) ?: mutable.value.durationUs
        mutable.value = mutable.value.copy(playing = engine.isPlaying,
            ready = engine.playerError == null && (engine.playbackState == Player.STATE_READY || engine.playbackState == Player.STATE_ENDED),
            loading = engine.playbackState == Player.STATE_BUFFERING,
            positionUs = engine.currentPosition.coerceIn(0, Long.MAX_VALUE / 1000) * 1000, durationUs = duration,
            error = engine.playerError?.let { "成果试听失败（${it.errorCodeName}）" })
    }
    fun seek(fraction: Float) { player?.seekTo((mutable.value.durationUs / 1000 * fraction.coerceIn(0f, 1f)).toLong()); publish() }
    fun pause() { wantedPlayback = false; player?.pause(); publish() }
    fun renamed(id: String, name: String) { if (mutable.value.id == id) mutable.value = mutable.value.copy(name = name) }
    fun stop(id: String? = null) {
        if (id != null && mutable.value.id != id) return
        val hadAccess = access != null
        wantedPlayback = false
        generation++; opening?.cancel(); opening = null
        handler.removeCallbacks(ticker); player?.stop(); player?.clearMediaItems()
        access?.close(); access = null; mutable.value = ResultPlaybackState()
        if (hadAccess) scope.launch { if (!repository.cleanupResults()) warn("部分无引用成果暂未清理") }
    }
    fun release() { stop(); player?.release(); player = null }
}
