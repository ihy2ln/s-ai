package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EqualizerTest {

    private fun flatBands() = DoubleArray(Equalizer.BAND_FREQS_HZ.size)

    @Test
    fun `equalizer preserves length`() {
        val wav = sineWav(frames = 2000)
        val eqd = Equalizer.apply(wav, flatBands(), lowCutHz = 20.0, midCutHz = 0.0, highCutHz = 20000.0)
        assertEquals(wav.samples.size, eqd.samples.size)
    }

    @Test
    fun `silence stays silent through the equalizer`() {
        val silence = Wav(44100, 1, ShortArray(500))
        val bands = DoubleArray(Equalizer.BAND_FREQS_HZ.size) { 8.0 }
        val eqd = Equalizer.apply(silence, bands, lowCutHz = 100.0, midCutHz = 1000.0, highCutHz = 8000.0)
        assertTrue(eqd.samples.all { it == 0.toShort() })
    }

    @Test
    fun `requires one gain per band`() {
        val wav = sineWav(frames = 100)
        var threw = false
        try {
            Equalizer.apply(wav, DoubleArray(3), lowCutHz = 20.0, midCutHz = 0.0, highCutHz = 20000.0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "a mismatched band-gain array should be rejected")
    }

    @Test
    fun `boosting the lowest band raises energy of a low-frequency tone`() {
        val bass = sineWav(freqHz = 60.0, frames = 4410, amplitude = 0.3)
        val flat = Equalizer.apply(bass, flatBands(), lowCutHz = 20.0, midCutHz = 0.0, highCutHz = 20000.0)
        val boosted = Equalizer.apply(bass, flatBands().also { it[0] = 12.0 }, lowCutHz = 20.0, midCutHz = 0.0, highCutHz = 20000.0)

        fun rms(samples: ShortArray): Double = kotlin.math.sqrt(samples.map { it.toDouble() * it }.average())

        assertTrue(rms(boosted.samples) > rms(flat.samples), "boosting the lowest band should raise energy of a 60Hz tone")
    }

    @Test
    fun `high cut reduces energy of a high-frequency tone`() {
        val treble = sineWav(freqHz = 8000.0, frames = 4410, amplitude = 0.3)
        val uncut = Equalizer.apply(treble, flatBands(), lowCutHz = 20.0, midCutHz = 0.0, highCutHz = 20000.0)
        val cut = Equalizer.apply(treble, flatBands(), lowCutHz = 20.0, midCutHz = 0.0, highCutHz = 1000.0)

        fun rms(samples: ShortArray): Double = kotlin.math.sqrt(samples.map { it.toDouble() * it }.average())

        assertTrue(rms(cut.samples) < rms(uncut.samples), "a 1kHz high-cut should attenuate an 8kHz tone")
    }
}
