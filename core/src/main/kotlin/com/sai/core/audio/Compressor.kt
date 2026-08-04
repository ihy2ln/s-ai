package com.sai.core.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/** A feed-forward peak compressor: gain reduction above [thresholdDb], eased in/out by attack/release. */
object Compressor {

    /**
     * @param thresholdDb level above which gain reduction kicks in, e.g. -24..0
     * @param ratio input:output ratio above threshold, 1 = no compression, higher = more
     * @param attackMs how fast gain reduction engages
     * @param releaseMs how fast gain reduction relaxes
     * @param makeupGainDb gain applied after compression to restore level
     */
    fun apply(
        wav: Wav,
        thresholdDb: Double,
        ratio: Double,
        attackMs: Double,
        releaseMs: Double,
        makeupGainDb: Double,
    ): Wav {
        val safeRatio = ratio.coerceAtLeast(1.0)
        val attackCoeff = timeConstant(attackMs, wav.sampleRate)
        val releaseCoeff = timeConstant(releaseMs, wav.sampleRate)
        val makeup = 10.0.pow(makeupGainDb / 20.0)

        val out = ShortArray(wav.samples.size)

        for (channel in 0 until wav.channels) {
            var envelope = 0.0
            var frame = channel
            while (frame < wav.samples.size) {
                val input = wav.samples[frame] / 32768.0
                val level = abs(input)
                val coeff = if (level > envelope) attackCoeff else releaseCoeff
                envelope = coeff * envelope + (1 - coeff) * level

                val levelDb = 20.0 * log10(envelope.coerceAtLeast(1e-6))
                val gainReductionDb = if (levelDb > thresholdDb) {
                    (thresholdDb + (levelDb - thresholdDb) / safeRatio) - levelDb
                } else {
                    0.0
                }
                val gain = 10.0.pow(gainReductionDb / 20.0) * makeup
                out[frame] = (input * gain * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private fun timeConstant(ms: Double, sampleRate: Int): Double =
        exp(-1.0 / (ms.coerceAtLeast(0.1) / 1000.0 * sampleRate))
}
