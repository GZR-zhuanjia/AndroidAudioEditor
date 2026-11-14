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
import androidx.media3.exoplayer.SeekParameters
import com.audioassistant.editing.SegmentTimeline
import com.audioassistant.model.AudioSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PreviewMode(val label: String) { EDITED("编辑结果"), ORIGINAL("原曲"), SELECTION("选区"), SEAM("接缝"), WORKSPACE("拼接试听") }
data class PlaybackStatus(
    val positionUs: Long = 0, val playing: Boolean = false, val ready: Boolean = false,
    val mode: PreviewMode = PreviewMode.EDITED, val error: String? = null,
)

/** 所有播放器访问都在主线程；真实片段播放，不生成临时音频或伪时间。 */
@androidx.annotation.OptIn(UnstableApi::class)
class AudioPreview(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(PlaybackStatus())
    val state = mutableState.asStateFlow()
    private var player: ExoPlayer? = null
    private var parts = emptyList<AudioSegment>()
    private var offsets = longArrayOf(0)
    private var baseUs = 0L
    private var generation = 0L
    private var ended: (() -> Unit)? = null
    private var released = false
    private val ticker = object : Runnable {
        override fun run() {
            if (released) return
            publish()
            if (player?.isPlaying == true) handler.postDelayed(this, 100)
        }
    }

    private fun engine(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { engine ->
        player = engine
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        engine.setHandleAudioBecomingNoisy(true)
        engine.setSeekParameters(SeekParameters.EXACT)
        engine.trackSelectionParameters = engine.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        engine.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                handler.removeCallbacks(ticker)
                if (isPlaying) handler.post(ticker)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                publish()
                if (playbackState == Player.STATE_ENDED) {
                    val token = generation
                    val callback = ended
                    handler.post { if (token == generation && !released) callback?.invoke() }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                mutableState.value = mutableState.value.copy(playing = false, ready = false,
                    error = "无法试听：文件授权、媒体格式或解码失败（${error.errorCodeName}）")
            }
        })
    }

    fun load(
        segments: List<AudioSegment>, uris: Map<String, Uri>, mode: PreviewMode,
        positionUs: Long = 0, baseUs: Long = 0, play: Boolean = false, onEnded: (() -> Unit)? = null,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        generation++
        ended = null
        player?.pause()
        parts = segments
        offsets = LongArray(parts.size + 1).also { values ->
            parts.forEachIndexed { index, part -> values[index + 1] = Math.addExact(values[index], part.durationUs) }
        }
        this.baseUs = baseUs
        mutableState.value = PlaybackStatus(baseUs + positionUs.coerceIn(0, SegmentTimeline.duration(parts)), mode = mode)
        if (parts.isEmpty()) { player?.clearMediaItems(); return }
        val items = parts.mapIndexed { index, part ->
            MediaItem.Builder().setMediaId("$generation:$index").setUri(uris.getValue(part.sourceId))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(part.sourceStartUs).setEndPositionUs(part.sourceEndUs).build()).build()
        }
        val (index, offsetUs) = locate(positionUs)
        engine().setMediaItems(items, index, offsetUs / 1000)
        ended = onEnded
        engine().prepare()
        engine().playWhenReady = play
    }

    private fun locate(positionUs: Long): Pair<Int, Long> {
        if (parts.isEmpty()) return 0 to 0L
        val position = positionUs.coerceIn(0, offsets.last())
        val result = offsets.binarySearch(position)
        val index = (if (result >= 0) result else -result - 2).coerceIn(0, parts.lastIndex)
        return index to (position - offsets[index])
    }

    fun positionUs(): Long {
        val engine = player ?: return mutableState.value.positionUs
        if (parts.isEmpty()) return baseUs
        val index = engine.currentMediaItemIndex.coerceIn(0, parts.lastIndex)
        val before = offsets[index]
        val within = (engine.currentPosition.coerceIn(0, Long.MAX_VALUE / 1000) * 1000).coerceAtMost(parts[index].durationUs)
        return baseUs + before + within
    }

    fun seek(positionUs: Long) {
        if (parts.isEmpty()) return
        val (index, offset) = locate(positionUs - baseUs)
        player?.seekTo(index, offset / 1000)
        publish()
    }
    fun play() {
        if (parts.isEmpty()) return
        if (positionUs() >= baseUs + SegmentTimeline.duration(parts)) seek(baseUs)
        player?.play()
    }
    fun pause() { player?.pause(); publish() }
    private fun publish() {
        val engine = player ?: return
        mutableState.value = mutableState.value.copy(positionUs = positionUs(), playing = engine.isPlaying,
            ready = parts.isNotEmpty() && (engine.playbackState == Player.STATE_READY || engine.playbackState == Player.STATE_ENDED) && engine.playerError == null,
            error = engine.playerError?.let { "无法试听：${it.errorCodeName}" })
    }
    fun clear() {
        generation++
        ended = null
        parts = emptyList()
        offsets = longArrayOf(0)
        baseUs = 0
        handler.removeCallbacks(ticker)
        player?.stop()
        player?.clearMediaItems()
        mutableState.value = PlaybackStatus()
    }
    fun release() { released = true; clear(); player?.release(); player = null }
}
