package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterTest {

    @Test
    fun `filter preserves length and channel layout`() {
        val wav = sineWav(channels = 2, frames = 2000)
        val filtered = Filter.apply(wav, cutoffHz = 800.0, resonance = 0.3, drive = 0.2)
        assertEquals(wav.samples.size, filtered.samples.size)
        assertEquals(wav.channels, filtered.channels)
    }

    @Test
    fun `silence stays silent through the filter`() {
        val silence = Wav(44100, 1, ShortArray(500))
        val filtered = Filter.apply(silence, cutoffHz = 2000.0, resonance = 0.8, drive = 0.9)
        assertTrue(filtered.samples.all { it == 0.toShort() })
    }

    @Test
    fun `output stays within 16-bit range even with heavy drive`() {
        val loud = sineWav(amplitude = 0.99, frames = 2000)
        val filtered = Filter.apply(loud, cutoffHz = 5000.0, resonance = 0.95, drive = 1.0)
        assertTrue(filtered.samples.all { it >= Short.MIN_VALUE && it <= Short.MAX_VALUE })
    }
}
