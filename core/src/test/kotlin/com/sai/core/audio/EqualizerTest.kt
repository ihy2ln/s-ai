package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EqualizerTest {

    @Test
    fun `equalizer preserves length`() {
        val wav = sineWav(frames = 2000)
        val eqd = Equalizer.apply(wav, lowGainDb = 4.0, midGainDb = -3.0, highGainDb = 6.0)
        assertEquals(wav.samples.size, eqd.samples.size)
    }

    @Test
    fun `silence stays silent through the equalizer`() {
        val silence = Wav(44100, 1, ShortArray(500))
        val eqd = Equalizer.apply(silence, lowGainDb = 10.0, midGainDb = -10.0, highGainDb = 8.0)
        assertTrue(eqd.samples.all { it == 0.toShort() })
    }

    @Test
    fun `boosting a band raises its energy relative to flat`() {
        val bass = sineWav(freqHz = 100.0, frames = 4410, amplitude = 0.3)
        val flat = Equalizer.apply(bass, lowGainDb = 0.0, midGainDb = 0.0, highGainDb = 0.0)
        val boosted = Equalizer.apply(bass, lowGainDb = 12.0, midGainDb = 0.0, highGainDb = 0.0)

        fun rms(samples: ShortArray): Double = kotlin.math.sqrt(samples.map { (it.toDouble()) * it }.average())

        assertTrue(rms(boosted.samples) > rms(flat.samples), "boosting the low shelf should raise energy of a low-frequency tone")
    }
}
