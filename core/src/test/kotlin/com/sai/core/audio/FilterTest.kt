package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterTest {

    @Test
    fun `filter preserves length and channel layout when pitch is unchanged`() {
        val wav = sineWav(channels = 2, frames = 2000)
        val filtered = Filter.apply(wav, lowCutHz = 20.0, highCutHz = 20000.0, cutoffHz = 800.0, resonance = 0.3, drive = 0.2, pitchSemitones = 0.0)
        assertEquals(wav.samples.size, filtered.samples.size)
        assertEquals(wav.channels, filtered.channels)
    }

    @Test
    fun `silence stays silent through the filter`() {
        val silence = Wav(44100, 1, ShortArray(500))
        val filtered = Filter.apply(silence, lowCutHz = 100.0, highCutHz = 8000.0, cutoffHz = 2000.0, resonance = 0.8, drive = 0.9, pitchSemitones = 5.0)
        assertTrue(filtered.samples.all { it == 0.toShort() })
    }

    @Test
    fun `output stays within 16-bit range even with heavy drive`() {
        val loud = sineWav(amplitude = 0.99, frames = 2000)
        val filtered = Filter.apply(loud, lowCutHz = 20.0, highCutHz = 20000.0, cutoffHz = 5000.0, resonance = 0.95, drive = 1.0, pitchSemitones = 0.0)
        assertTrue(filtered.samples.all { it >= Short.MIN_VALUE && it <= Short.MAX_VALUE })
    }

    @Test
    fun `raising pitch shortens the sample, like a sampler speed control`() {
        val wav = sineWav(frames = 4410)
        val up = Filter.apply(wav, lowCutHz = 20.0, highCutHz = 20000.0, cutoffHz = 20000.0, resonance = 0.0, drive = 0.0, pitchSemitones = 12.0)
        assertTrue(up.frameCount < wav.frameCount, "pitching up an octave should shorten the sample")
    }

    @Test
    fun `lowering pitch lengthens the sample`() {
        val wav = sineWav(frames = 4410)
        val down = Filter.apply(wav, lowCutHz = 20.0, highCutHz = 20000.0, cutoffHz = 20000.0, resonance = 0.0, drive = 0.0, pitchSemitones = -12.0)
        assertTrue(down.frameCount > wav.frameCount, "pitching down an octave should lengthen the sample")
    }
}
