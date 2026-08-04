package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReverbTest {

    @Test
    fun `reverb preserves length`() {
        val wav = sineWav(frames = 2000)
        val wet = Reverb.apply(wav, size = 0.5, damp = 0.5, mix = 0.4)
        assertEquals(wav.samples.size, wet.samples.size)
    }

    @Test
    fun `silence stays silent through the reverb`() {
        val silence = Wav(44100, 1, ShortArray(500))
        val wet = Reverb.apply(silence, size = 0.8, damp = 0.3, mix = 0.9)
        assertTrue(wet.samples.all { it == 0.toShort() })
    }

    @Test
    fun `zero mix returns effectively dry audio`() {
        val wav = sineWav(frames = 1000, amplitude = 0.4)
        val dry = Reverb.apply(wav, size = 0.5, damp = 0.5, mix = 0.0)
        for (i in wav.samples.indices) {
            assertTrue(kotlin.math.abs(wav.samples[i] - dry.samples[i]) <= 1, "mix=0 should leave audio essentially unchanged")
        }
    }
}
