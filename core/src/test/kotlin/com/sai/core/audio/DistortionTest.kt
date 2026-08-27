package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistortionTest {

    @Test
    fun `distortion preserves length`() {
        val wav = sineWav(frames = 1000, amplitude = 0.4)
        val wet = Distortion.apply(wav, drive = 0.8, tone = 0.5, mix = 1.0)
        assertEquals(wav.frameCount, wet.frameCount)
    }

    @Test
    fun `zero mix stays dry`() {
        val wav = sineWav(frames = 400, amplitude = 0.3)
        val dry = Distortion.apply(wav, drive = 1.0, tone = 0.2, mix = 0.0)
        for (i in wav.samples.indices) {
            assertTrue(abs(wav.samples[i] - dry.samples[i]) <= 1)
        }
    }
}
