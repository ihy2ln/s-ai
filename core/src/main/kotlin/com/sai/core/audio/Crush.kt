package com.sai.core.audio

import kotlin.math.roundToInt

/** Bit-depth and sample-rate reduction. Bits 4..16, rate 0.05..1 of original. */
object Crush {

    fun apply(wav: Wav, bits: Double, rate: Double, mix: Double): Wav {
        val levels = (2.0.powInt(bits.coerceIn(4.0, 16.0).roundToInt()) - 1).coerceAtLeast(2)
        val holdEvery = (1.0 / rate.coerceIn(0.05, 1.0)).roundToInt().coerceAtLeast(1)
        val wetMix = mix.coerceIn(0.0, 1.0)
        val out = ShortArray(wav.samples.size)
        for (channel in 0 until wav.channels) {
            var held = 0.0
            var frame = 0
            var index = channel
            while (index < wav.samples.size) {
                val dry = wav.samples[index] / 32768.0
                if (frame % holdEvery == 0) {
                    val quantized = kotlin.math.round(dry * levels) / levels
                    held = quantized
                }
                val mixed = dry * (1.0 - wetMix) + held * wetMix
                out[index] = (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame++
                index += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private fun Double.powInt(exp: Int): Int {
        var n = 1
        repeat(exp.coerceIn(1, 16)) { n *= 2 }
        return n
    }
}
