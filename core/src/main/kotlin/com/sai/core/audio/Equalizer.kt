package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos

/**
 * An 8-band graphic EQ (mimicking FL Studio's Parametric EQ 2 band layout) plus three
 * cutoff filters: a low cut (high-pass), a mid cut (notch), and a high cut (low-pass).
 */
object Equalizer {

    /** Fixed band centers, low to high, matching the 8-knob graphic-EQ layout. */
    val BAND_FREQS_HZ = doubleArrayOf(60.0, 150.0, 300.0, 600.0, 1200.0, 2400.0, 4800.0, 9600.0)

    /**
     * @param bandGainsDb 8 gains (low to high), typically -15..15, matching [BAND_FREQS_HZ]
     * @param lowCutHz high-pass cutoff; 20 (its minimum) means effectively no cut
     * @param midCutHz notch center frequency; 0 means the mid cut is disabled
     * @param highCutHz low-pass cutoff; 20000 (its maximum) means effectively no cut
     */
    fun apply(
        wav: Wav,
        bandGainsDb: DoubleArray,
        lowCutHz: Double,
        midCutHz: Double,
        highCutHz: Double,
    ): Wav {
        require(bandGainsDb.size == BAND_FREQS_HZ.size) { "Expected ${BAND_FREQS_HZ.size} band gains, got ${bandGainsDb.size}" }
        val out = ShortArray(wav.samples.size)
        val midCutEnabled = midCutHz > 20.0

        for (channel in 0 until wav.channels) {
            val bands = BAND_FREQS_HZ.indices.map { i -> Biquad.peak(wav.sampleRate, BAND_FREQS_HZ[i], 1.2, bandGainsDb[i]) }
            val lowCut = Biquad.highPass(wav.sampleRate, lowCutHz.coerceIn(20.0, 2000.0))
            val highCut = Biquad.lowPass(wav.sampleRate, highCutHz.coerceIn(1000.0, 20000.0))
            val midCut = if (midCutEnabled) Biquad.notch(wav.sampleRate, midCutHz.coerceIn(21.0, 12000.0), 2.5) else null

            var frame = channel
            while (frame < wav.samples.size) {
                var sample = wav.samples[frame] / 32768.0
                for (band in bands) sample = band.process(sample)
                sample = lowCut.process(sample)
                sample = highCut.process(sample)
                if (midCut != null) sample = midCut.process(sample)
                out[frame] = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private class Biquad(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x0: Double): Double {
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
            return y0
        }

        companion object {
            fun peak(sampleRate: Int, freq: Double, q: Double, gainDb: Double): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * freq / sampleRate
                val cosw0 = cos(w0)
                val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.01))

                val b0 = 1 + alpha * a
                val b1 = -2 * cosw0
                val b2 = 1 - alpha * a
                val a0 = 1 + alpha / a
                val a1 = -2 * cosw0
                val a2 = 1 - alpha / a
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun highPass(sampleRate: Int, freq: Double): Biquad {
                val w0 = 2 * PI * freq / sampleRate
                val cosw0 = cos(w0)
                val alpha = sin(w0) / (2.0 * 0.707)

                val b0 = (1 + cosw0) / 2
                val b1 = -(1 + cosw0)
                val b2 = (1 + cosw0) / 2
                val a0 = 1 + alpha
                val a1 = -2 * cosw0
                val a2 = 1 - alpha
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun lowPass(sampleRate: Int, freq: Double): Biquad {
                val w0 = 2 * PI * freq / sampleRate
                val cosw0 = cos(w0)
                val alpha = sin(w0) / (2.0 * 0.707)

                val b0 = (1 - cosw0) / 2
                val b1 = 1 - cosw0
                val b2 = (1 - cosw0) / 2
                val a0 = 1 + alpha
                val a1 = -2 * cosw0
                val a2 = 1 - alpha
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun notch(sampleRate: Int, freq: Double, q: Double): Biquad {
                val w0 = 2 * PI * freq / sampleRate
                val cosw0 = cos(w0)
                val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.01))

                val b0 = 1.0
                val b1 = -2 * cosw0
                val b2 = 1.0
                val a0 = 1 + alpha
                val a1 = -2 * cosw0
                val a2 = 1 - alpha
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }
        }
    }
}
