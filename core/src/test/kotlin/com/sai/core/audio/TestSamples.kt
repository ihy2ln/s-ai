package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.sin

fun sineWav(
    sampleRate: Int = 44100,
    channels: Int = 1,
    frames: Int = 4410,
    freqHz: Double = 440.0,
    amplitude: Double = 0.5,
): Wav {
    val samples = ShortArray(frames * channels)
    for (frame in 0 until frames) {
        val t = frame.toDouble() / sampleRate
        val value = (amplitude * Short.MAX_VALUE * sin(2 * PI * freqHz * t)).toInt().toShort()
        for (c in 0 until channels) samples[frame * channels + c] = value
    }
    return Wav(sampleRate, channels, samples)
}
