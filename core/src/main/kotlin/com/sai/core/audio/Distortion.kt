package com.sai.core.audio

import kotlin.math.tanh

/** Soft-clip distortion with a post low-pass "tone" control. */
object Distortion {

    fun apply(wav: Wav, drive: Double, tone: Double, mix: Double): Wav {
        val amount = 1.0 + drive.coerceIn(0.0, 1.0) * 24.0
        val wetMix = mix.coerceIn(0.0, 1.0)
        val cutoffHz = 400.0 + tone.coerceIn(0.0, 1.0) * 16000.0
        val alpha = lowPassAlpha(cutoffHz, wav.sampleRate)
        val out = ShortArray(wav.samples.size)

        for (channel in 0 until wav.channels) {
            var lp = 0.0
            var frame = channel
            while (frame < wav.samples.size) {
                val dry = wav.samples[frame] / 32768.0
                val driven = tanh(dry * amount)
                lp += alpha * (driven - lp)
                val mixed = dry * (1.0 - wetMix) + lp * wetMix
                out[frame] = (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private fun lowPassAlpha(cutoffHz: Double, sampleRate: Int): Double {
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz.coerceIn(40.0, 20000.0))
        val dt = 1.0 / sampleRate
        return dt / (rc + dt)
    }
}
