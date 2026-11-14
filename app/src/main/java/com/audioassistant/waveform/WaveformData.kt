package com.audioassistant.waveform

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

/** Arrays are privately owned after parsing, shared read-only by all viewport renders. */
class WaveformLevel(val framesPerBin: Long, internal val peaks: FloatArray, internal val rms: FloatArray)
class WaveformData(val sampleRate: Int, val frames: Long, val maximum: Float, val levels: List<WaveformLevel>) {
    val durationUs: Long get() = frames * 1_000_000 / sampleRate

    /** Select coarse levels for complete bins; use finest bins at interval edges. */
    fun amplitudes(fromUs: Long, toUs: Long): Pair<Float, Float> {
        val from = (fromUs.coerceAtLeast(0) * sampleRate / 1_000_000).coerceAtMost(frames)
        val to = ((toUs.coerceAtLeast(0) * sampleRate + 999_999) / 1_000_000).coerceAtMost(frames)
        if (to <= from) return 0f to 0f
        var cursor = from
        var peak = 0f
        var squares = 0.0
        while (cursor < to) {
            val level = levels.lastOrNull {
                cursor % it.framesPerBin == 0L && cursor + it.framesPerBin <= to
            } ?: levels.first()
            val index = (cursor / level.framesPerBin).toInt()
            if (index !in level.peaks.indices) break
            val end = minOf(to, (index + 1L) * level.framesPerBin, frames)
            peak = maxOf(peak, level.peaks[index])
            squares += level.rms[index].toDouble() * level.rms[index] * (end - cursor)
            cursor = end
        }
        return peak to sqrt(squares / (to - from)).toFloat()
    }

    companion object {
        // AAWAVE01, little endian. Parse on IO; reject incomplete/unbounded caches.
        fun read(file: File): WaveformData = DataInputStream(file.inputStream().buffered()).use { input ->
            fun u32(): Long = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            fun i64(): Long = java.lang.Long.reverseBytes(input.readLong())
            fun f32(): Float = Float.fromBits(Integer.reverseBytes(input.readInt()))
            fun ensure(value: Boolean) { if (!value) throw IOException("波形缓存格式或数据无效") }
            val magic = ByteArray(8).also { input.readFully(it) }
            ensure(magic.contentEquals("AAWAVE01".toByteArray(Charsets.US_ASCII)) && u32() == 1L)
            val rate = u32().toInt()
            ensure(rate in 1..384_000)
            val frames = i64()
            ensure(frames in 1..rate.toLong() * 8 * 60 * 60)
            val maximum = f32()
            ensure(maximum.isFinite() && maximum >= 0)
            val count = u32().toInt()
            ensure(count in 1..16)
            var expectedStep = (rate + 49L) / 50L
            val levels = List(count) {
                val step = i64()
                val size = u32()
                ensure(step == expectedStep && size == (frames + step - 1) / step && size in 1..1_440_000)
                val peaks = FloatArray(size.toInt())
                val rms = FloatArray(size.toInt())
                for (i in peaks.indices) {
                    peaks[i] = f32(); rms[i] = f32()
                    ensure(peaks[i].isFinite() && rms[i].isFinite() && rms[i] >= 0 && peaks[i] >= 0 &&
                        peaks[i] <= maximum && rms[i] <= peaks[i] + 0.00001f)
                }
                expectedStep *= 4
                WaveformLevel(step, peaks, rms)
            }
            ensure(levels.last().peaks.size == 1 && input.read() == -1)
            WaveformData(rate, frames, maximum, levels)
        }
    }
}
