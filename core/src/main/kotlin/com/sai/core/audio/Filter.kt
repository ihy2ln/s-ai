package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.tanh

/**
 * A 6-knob "synth" sound shaper: low cut and high cut trim the ends of the spectrum, a
 * resonant cutoff/resonance pair carves the main tone, drive adds saturation, and pitch
 * re-speeds the sample (classic sampler-style varispeed pitch shift).
 */
object Filter {

    /**
     * @param lowCutHz high-pass corner, 20..5000 (20 = effectively off)
     * @param highCutHz low-pass corner, 200..20000 (20000 = effectively off)
     * @param cutoffHz resonant filter corner, 20..20000
     * @param resonance 0..1, higher rings more around the cutoff
     * @param drive 0..1, 0 = clean, 1 = heavily saturated ("crunch")
     * @param pitchSemitones -24..24, resamples the audio to shift pitch (and duration) like a sampler's speed control
     */
    fun apply(
        wav: Wav,
        lowCutHz: Double,
        highCutHz: Double,
        cutoffHz: Double,
        resonance: Double,
        drive: Double,
        pitchSemitones: Double,
    ): Wav {
        val shaped = shapeFrequency(wav, lowCutHz, highCutHz, cutoffHz, resonance, drive)
        return if (pitchSemitones == 0.0) shaped else resample(shaped, pitchSemitones)
    }

    private fun shapeFrequency(
        wav: Wav,
        lowCutHz: Double,
        highCutHz: Double,
        cutoffHz: Double,
        resonance: Double,
        drive: Double,
    ): Wav {
        val cutoff = cutoffHz.coerceIn(20.0, 20000.0)
        val q = (1.0 - resonance.coerceIn(0.0, 1.0)).coerceAtLeast(0.05)
        val hpAlpha = highPassAlpha(lowCutHz.coerceIn(20.0, 5000.0), wav.sampleRate)
        val lpAlpha = lowPassAlpha(highCutHz.coerceIn(200.0, 20000.0), wav.sampleRate)
        val out = ShortArray(wav.samples.size)

        for (channel in 0 until wav.channels) {
            var low = 0.0
            var band = 0.0
            val f = 2.0 * kotlin.math.sin(PI * cutoff / wav.sampleRate).coerceIn(0.0, 1.0)

            var hpPrevIn = 0.0
            var hpPrevOut = 0.0
            var lpPrevOut = 0.0

            var frame = channel
            while (frame < wav.samples.size) {
                var sample = wav.samples[frame] / 32768.0

                val hp = hpAlpha * (hpPrevOut + sample - hpPrevIn)
                hpPrevIn = sample
                hpPrevOut = hp
                sample = hp

                val lp = lpPrevOut + lpAlpha * (sample - lpPrevOut)
                lpPrevOut = lp
                sample = lp

                val high = sample - low - q * band
                band += f * high
                low += f * band
                val driven = softClip(low, drive)
                out[frame] = (driven * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private fun resample(wav: Wav, semitones: Double): Wav {
        val rate = 2.0.pow(semitones / 12.0)
        val srcFrames = wav.frameCount
        val dstFrames = (srcFrames / rate).toInt().coerceAtLeast(1)
        val out = ShortArray(dstFrames * wav.channels)

        for (i in 0 until dstFrames) {
            val srcPos = i * rate
            val srcIndex = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val nextIndex = (srcIndex + 1).coerceAtMost(srcFrames - 1)
            val frac = srcPos - srcIndex
            for (c in 0 until wav.channels) {
                val a = wav.samples[srcIndex * wav.channels + c]
                val b = wav.samples[nextIndex * wav.channels + c]
                out[i * wav.channels + c] = (a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return wav.copy(samples = out)
    }

    private fun lowPassAlpha(cutoffHz: Double, sampleRate: Int): Double {
        val rc = 1.0 / (2 * PI * cutoffHz)
        val dt = 1.0 / sampleRate
        return dt / (rc + dt)
    }

    private fun highPassAlpha(cutoffHz: Double, sampleRate: Int): Double {
        val rc = 1.0 / (2 * PI * cutoffHz)
        val dt = 1.0 / sampleRate
        return rc / (rc + dt)
    }

    private fun softClip(x: Double, drive: Double): Double {
        if (drive <= 0.0) return x
        val amount = 1.0 + drive * 9.0
        return tanh(x * amount) / tanh(amount)
    }
}
