package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.sin

/** Slow allpass phaser. Rate in Hz, depth and mix 0..1. */
object Phaser {

    fun apply(wav: Wav, rateHz: Double, depth: Double, mix: Double): Wav {
        val rate = rateHz.coerceIn(0.05, 8.0)
        val depthAmt = depth.coerceIn(0.0, 1.0)
        val wetMix = mix.coerceIn(0.0, 1.0)
        val out = ShortArray(wav.samples.size)
        for (channel in 0 until wav.channels) {
            val stages = Array(4) { Allpass() }
            var frame = 0
            var index = channel
            while (index < wav.samples.size) {
                val dry = wav.samples[index] / 32768.0
                val lfo = 0.5 + 0.5 * sin(2.0 * PI * rate * frame / wav.sampleRate)
                val delay = 1.2 + depthAmt * 3.5 * lfo
                var wet = dry
                for (stage in stages) wet = stage.process(wet, delay)
                val mixed = dry * (1.0 - wetMix) + wet * wetMix
                out[index] = (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame++
                index += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private class Allpass {
        private var stored = 0.0
        fun process(input: Double, delay: Double): Double {
            val coeff = (1.0 - delay) / (1.0 + delay)
            val output = -input + stored
            stored = input + output * coeff.coerceIn(-0.95, 0.95)
            return output
        }
    }
}
