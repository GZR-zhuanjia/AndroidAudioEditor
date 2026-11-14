package com.audioassistant.engine

/** 仅声明能力边界；阶段 5 才实现 JNI。没有模拟实现或原生库加载。 */
interface NativeAudioEngine {
    suspend fun probe(localInputPath: String): AudioInfo
}

data class AudioInfo(
    val durationUs: Long,
    val codecName: String,
    val sampleRateHz: Int,
    val channelCount: Int,
)
