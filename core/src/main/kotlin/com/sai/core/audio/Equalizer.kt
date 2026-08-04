package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/** A lean 3-band EQ (low shelf, mid peak, high shelf) built from standard biquad filters. */
object Equalizer {

    /**
     * @param lowGainDb shelf gain below ~150Hz, typically -15..15
     * @param midGainDb peak gain around ~1kHz, typically -15..15
     * @param highGainDb shelf gain above ~6kHz, typically -15..15
     */
    fun apply(wav: Wav, lowGainDb: Double, midGainDb: Double, highGainDb: Double): Wav {
        val out = ShortArray(wav.samples.size)
        for (channel in 0 until wav.channels) {
            val low = Biquad.lowShelf(wav.sampleRate, 150.0, lowGainDb)
            val mid = Biquad.peak(wav.sampleRate, 1000.0, 1.0, midGainDb)
            val high = Biquad.highShelf(wav.sampleRate, 6000.0, highGainDb)

            var frame = channel
            while (frame < wav.samples.size) {
                var sample = wav.samples[frame] / 32768.0
                sample = low.process(sample)
                sample = mid.process(sample)
                sample = high.process(sample)
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
            fun lowShelf(sampleRate: Int, freq: Double, gainDb: Double): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * freq / sampleRate
                val cosw0 = cos(w0)
                val sinw0 = sin(w0)
                val alpha = sinw0 / 2.0 * sqrt((a + 1 / a) * (1 / 0.9 - 1) + 2)
                val twoSqrtAAlpha = 2 * sqrt(a) * alpha

                val b0 = a * ((a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha)
                val b1 = 2 * a * ((a - 1) - (a + 1) * cosw0)
                val b2 = a * ((a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha)
                val a0 = (a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha
                val a1 = -2 * ((a - 1) + (a + 1) * cosw0)
                val a2 = (a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun highShelf(sampleRate: Int, freq: Double, gainDb: Double): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * freq / sampleRate
                val cosw0 = cos(w0)
                val sinw0 = sin(w0)
                val alpha = sinw0 / 2.0 * sqrt((a + 1 / a) * (1 / 0.9 - 1) + 2)
                val twoSqrtAAlpha = 2 * sqrt(a) * alpha

                val b0 = a * ((a + 1) + (a - 1) * cosw0 + twoSqrtAAlpha)
                val b1 = -2 * a * ((a - 1) + (a + 1) * cosw0)
                val b2 = a * ((a + 1) + (a - 1) * cosw0 - twoSqrtAAlpha)
                val a0 = (a + 1) - (a - 1) * cosw0 + twoSqrtAAlpha
                val a1 = 2 * ((a - 1) - (a + 1) * cosw0)
                val a2 = (a + 1) - (a - 1) * cosw0 - twoSqrtAAlpha
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun peak(sampleRate: Int, freq: Double, q: Double, gainDb: Double): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2 * PI * freq / sampleRate
                val cosw0 = cos(w0)
                val sinw0 = sin(w0)
                val alpha = sinw0 / (2.0 * q.coerceAtLeast(0.01))

                val b0 = 1 + alpha * a
                val b1 = -2 * cosw0
                val b2 = 1 - alpha * a
                val a0 = 1 + alpha / a
                val a1 = -2 * cosw0
                val a2 = 1 - alpha / a
                return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }
        }
    }
}
