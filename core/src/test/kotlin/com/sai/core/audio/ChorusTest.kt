package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChorusTest {

    @Test
    fun `chorus preserves length`() {
        val wav = sineWav(frames = 2000, amplitude = 0.4)
        val wet = Chorus.apply(wav, rateHz = 1.2, depth = 0.6, mix = 0.5)
        assertEquals(wav.frameCount, wet.frameCount)
    }

    @Test
    fun `zero mix stays dry`() {
        val wav = sineWav(frames = 800, amplitude = 0.4)
        val dry = Chorus.apply(wav, rateHz = 2.0, depth = 1.0, mix = 0.0)
        for (i in wav.samples.indices) {
            assertTrue(abs(wav.samples[i] - dry.samples[i]) <= 1)
        }
    }
}
