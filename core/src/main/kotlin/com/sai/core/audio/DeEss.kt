package com.sai.core.audio

import kotlin.math.abs

/** Dynamic high-frequency de-esser around 6–8 kHz. */
object DeEss {

    fun apply(wav: Wav, amount: Double, frequency: Double): Wav {
        val mix = amount.coerceIn(0.0, 1.0)
        if (mix <= 0.0) return wav
        val freq = frequency.coerceIn(4000.0, 12000.0)
        val hpAlpha = highPassAlpha(freq, wav.sampleRate)
        val out = ShortArray(wav.samples.size)
        for (channel in 0 until wav.channels) {
            var prevIn = 0.0
            var prevOut = 0.0
            var env = 0.0
            var index = channel
            while (index < wav.samples.size) {
                val dry = wav.samples[index] / 32768.0
                val hp = hpAlpha * (prevOut + dry - prevIn)
                prevIn = dry
                prevOut = hp
                val level = abs(hp)
                env = 0.9 * env + 0.1 * level
                val reduction = (1.0 - (env * mix * 3.0).coerceIn(0.0, 0.85))
                val mixed = dry - hp * (1.0 - reduction)
                out[index] = (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                index += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private fun highPassAlpha(cutoffHz: Double, sampleRate: Int): Double {
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        val dt = 1.0 / sampleRate
        return rc / (rc + dt)
    }
}
