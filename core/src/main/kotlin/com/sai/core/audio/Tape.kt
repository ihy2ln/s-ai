package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

/** Tape-style saturation plus slow wow. Drive, wow, and mix 0..1. */
object Tape {

    fun apply(wav: Wav, drive: Double, wow: Double, mix: Double): Wav {
        val amount = 1.0 + drive.coerceIn(0.0, 1.0) * 8.0
        val wowAmt = wow.coerceIn(0.0, 1.0)
        val wetMix = mix.coerceIn(0.0, 1.0)
        val out = ShortArray(wav.samples.size)
        for (channel in 0 until wav.channels) {
            var lp = 0.0
            var frame = 0
            var index = channel
            while (index < wav.samples.size) {
                val dry = wav.samples[index] / 32768.0
                val lfo = 1.0 + wowAmt * 0.012 * sin(2.0 * PI * 0.35 * frame / wav.sampleRate)
                val driven = tanh(dry * amount * lfo)
                lp += 0.18 * (driven - lp)
                val mixed = dry * (1.0 - wetMix) + lp * wetMix
                out[index] = (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame++
                index += wav.channels
            }
        }
        return wav.copy(samples = out)
    }
}
