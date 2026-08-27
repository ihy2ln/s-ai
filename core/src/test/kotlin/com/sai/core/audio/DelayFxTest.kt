package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class DelayFxTest {

    @Test
    fun `delay pads a tail when mix is up`() {
        val wav = sineWav(frames = 800, amplitude = 0.5)
        val wet = DelayFx.apply(wav, timeMs = 120.0, feedback = 0.4, mix = 0.5)
        assertTrue(wet.frameCount > wav.frameCount)
        assertTrue(DelayFx.tailFrames(120.0, 0.4, 0.5, wav.sampleRate) > 0)
    }

    @Test
    fun `zero mix keeps the original length dry`() {
        val wav = sineWav(frames = 400, amplitude = 0.4)
        val dry = DelayFx.apply(wav, timeMs = 200.0, feedback = 0.5, mix = 0.0)
        for (i in wav.samples.indices) {
            assertTrue(abs(wav.samples[i] - dry.samples[i]) <= 1)
        }
    }
}
