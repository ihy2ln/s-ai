package com.sai.core.audio

import kotlin.math.abs
import kotlin.math.exp

/** Downward expander / noise gate. Threshold 0..1 linear, attack/release in ms. */
object Gate {

    fun apply(wav: Wav, threshold: Double, attackMs: Double, releaseMs: Double): Wav {
        val thresh = threshold.coerceIn(0.001, 0.5)
        val attackCoeff = exp(-1.0 / (attackMs.coerceAtLeast(0.5) / 1000.0 * wav.sampleRate))
        val releaseCoeff = exp(-1.0 / (releaseMs.coerceAtLeast(1.0) / 1000.0 * wav.sampleRate))
        val out = ShortArray(wav.samples.size)
        for (channel in 0 until wav.channels) {
            var env = 0.0
            var gain = 0.0
            var index = channel
            while (index < wav.samples.size) {
                val input = wav.samples[index] / 32768.0
                val level = abs(input)
                val coeff = if (level > env) attackCoeff else releaseCoeff
                env = coeff * env + (1.0 - coeff) * level
                val target = if (env >= thresh) 1.0 else (env / thresh).coerceIn(0.0, 1.0)
                val gCoeff = if (target > gain) attackCoeff else releaseCoeff
                gain = gCoeff * gain + (1.0 - gCoeff) * target
                out[index] = (input * gain * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                index += wav.channels
            }
        }
        return wav.copy(samples = out)
    }
}
