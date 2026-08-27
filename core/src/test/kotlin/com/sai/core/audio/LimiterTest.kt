package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class LimiterTest {

    @Test
    fun `limiter pulls a hot peak down`() {
        val wav = sineWav(frames = 2000, amplitude = 0.95)
        val limited = Limiter.apply(wav, thresholdDb = -6.0, releaseMs = 40.0)
        val dryPeak = wav.samples.maxOf { abs(it.toInt()) }
        val wetPeak = limited.samples.maxOf { abs(it.toInt()) }
        assertTrue(wetPeak < dryPeak, "limiter should reduce peak ($wetPeak vs $dryPeak)")
    }

    @Test
    fun `silence stays silent`() {
        val silence = Wav(44100, 1, ShortArray(400))
        val wet = Limiter.apply(silence, thresholdDb = -1.0, releaseMs = 50.0)
        assertTrue(wet.samples.all { it == 0.toShort() })
    }
}
