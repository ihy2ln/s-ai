package com.sai.core.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Brick-wall peak limiter: gain reduction above [thresholdDb], with a release time. */
object Limiter {

    fun apply(wav: Wav, thresholdDb: Double, releaseMs: Double): Wav {
        val threshold = 10.0.pow(thresholdDb.coerceIn(-24.0, 0.0) / 20.0)
        val releaseCoeff = exp(-1.0 / (releaseMs.coerceAtLeast(1.0) / 1000.0 * wav.sampleRate))
        val out = ShortArray(wav.samples.size)

        for (channel in 0 until wav.channels) {
            var gain = 1.0
            var frame = channel
            while (frame < wav.samples.size) {
                val input = wav.samples[frame] / 32768.0
                val peak = abs(input)
                val needed = if (peak > threshold && peak > 1e-9) threshold / peak else 1.0
                gain = if (needed < gain) needed else releaseCoeff * gain + (1.0 - releaseCoeff) * needed
                out[frame] = (input * gain * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame += wav.channels
            }
        }
        return wav.copy(samples = out)
    }
}
