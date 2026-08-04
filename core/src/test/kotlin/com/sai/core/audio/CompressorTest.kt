package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressorTest {

    @Test
    fun `compressor preserves length`() {
        val wav = sineWav(frames = 2000)
        val compressed = Compressor.apply(wav, thresholdDb = -20.0, ratio = 4.0, attackMs = 5.0, releaseMs = 50.0, makeupGainDb = 0.0)
        assertEquals(wav.samples.size, compressed.samples.size)
    }

    @Test
    fun `silence stays silent through the compressor`() {
        val silence = Wav(44100, 1, ShortArray(500))
        val compressed = Compressor.apply(silence, thresholdDb = -30.0, ratio = 8.0, attackMs = 1.0, releaseMs = 20.0, makeupGainDb = 6.0)
        assertTrue(compressed.samples.all { it == 0.toShort() })
    }

    @Test
    fun `a loud signal above threshold is reduced towards the threshold`() {
        val loud = sineWav(amplitude = 0.95, frames = 8000, freqHz = 220.0)
        val compressed = Compressor.apply(loud, thresholdDb = -12.0, ratio = 10.0, attackMs = 1.0, releaseMs = 20.0, makeupGainDb = 0.0)
        val loudPeak = loud.samples.maxOf { abs(it.toInt()) }
        val compressedPeak = compressed.samples.maxOf { abs(it.toInt()) }
        assertTrue(compressedPeak < loudPeak, "high-ratio compression above threshold should reduce peak level")
    }
}
