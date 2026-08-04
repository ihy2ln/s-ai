package com.sai.core.audio

data class Wav(
    val sampleRate: Int,
    val channels: Int,
    val samples: ShortArray,
) {
    val frameCount: Int get() = samples.size / channels

    override fun equals(other: Any?): Boolean = this === other || (
        other is Wav &&
            sampleRate == other.sampleRate &&
            channels == other.channels &&
            samples.contentEquals(other.samples)
        )

    override fun hashCode(): Int = 31 * (31 * sampleRate + channels) + samples.contentHashCode()
}
