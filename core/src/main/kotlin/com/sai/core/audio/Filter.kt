package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.tanh

/** A simple resonant low-pass "synth" filter plus a soft-clip drive stage, mirroring a macro-style filter knob set. */
object Filter {

    /**
     * @param cutoffHz corner frequency, 20..20000
     * @param resonance 0..1, higher rings more around the cutoff
     * @param drive 0..1, 0 = clean, 1 = heavily saturated ("crunch")
     */
    fun apply(wav: Wav, cutoffHz: Double, resonance: Double, drive: Double): Wav {
        val cutoff = cutoffHz.coerceIn(20.0, 20000.0)
        val q = (1.0 - resonance.coerceIn(0.0, 1.0)).coerceAtLeast(0.05)
        val out = ShortArray(wav.samples.size)

        for (channel in 0 until wav.channels) {
            var low = 0.0
            var band = 0.0
            val f = 2.0 * kotlin.math.sin(PI * cutoff / wav.sampleRate).coerceIn(0.0, 1.0)
            var frame = channel
            while (frame < wav.samples.size) {
                val input = wav.samples[frame] / 32768.0
                val high = input - low - q * band
                band += f * high
                low += f * band
                val driven = softClip(low, drive)
                out[frame] = (driven * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private fun softClip(x: Double, drive: Double): Double {
        if (drive <= 0.0) return x
        val amount = 1.0 + drive * 9.0
        return tanh(x * amount) / tanh(amount)
    }
}
