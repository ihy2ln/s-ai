package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.sin

/** Two-tap modulated delay chorus. Rate in Hz, depth and mix 0..1. */
object Chorus {

    fun apply(wav: Wav, rateHz: Double, depth: Double, mix: Double): Wav {
        val rate = rateHz.coerceIn(0.05, 8.0)
        val depthMs = 2.0 + depth.coerceIn(0.0, 1.0) * 10.0
        val wetMix = mix.coerceIn(0.0, 1.0)
        val maxDelay = ((depthMs / 1000.0) * wav.sampleRate).toInt().coerceAtLeast(2)
        val baseDelay = (maxDelay * 0.45).toInt().coerceAtLeast(1)
        val line = DoubleArray((wav.frameCount + maxDelay + 8) * wav.channels)
        val out = ShortArray(wav.samples.size)

        for (channel in 0 until wav.channels) {
            for (frame in 0 until wav.frameCount) {
                val input = wav.samples[frame * wav.channels + channel] / 32768.0
                line[(frame + maxDelay) * wav.channels + channel] = input
                val lfo = sin(2.0 * PI * rate * frame / wav.sampleRate)
                val delay = baseDelay + ((maxDelay - baseDelay) * (0.5 + 0.5 * lfo)).toInt()
                val read = ((frame + maxDelay) - delay).coerceAtLeast(0)
                val wet = line[read * wav.channels + channel]
                val mixed = input * (1.0 - wetMix) + wet * wetMix
                out[frame * wav.channels + channel] =
                    (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return wav.copy(samples = out)
    }
}
