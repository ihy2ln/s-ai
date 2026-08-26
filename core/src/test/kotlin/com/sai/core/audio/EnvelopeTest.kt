package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvelopeTest {

    @Test
    fun `identity envelope leaves a full-scale sample unchanged`() {
        val wav = sineWav(frames = 64, amplitude = 0.5)
        val shaped = Envelope.apply(wav, attackSec = 0.0, decaySec = 0.0, sustain = 1.0, releaseSec = 0.0)
        assertTrue(wav.samples.contentEquals(shaped.samples))
    }

    @Test
    fun `zero sustain silences the middle of the sound`() {
        val wav = sineWav(frames = 4410, amplitude = 0.8)
        val shaped = Envelope.apply(wav, attackSec = 0.0, decaySec = 0.0, sustain = 0.0, releaseSec = 0.0)
        val mid = shaped.samples[shaped.samples.size / 2]
        assertEquals(0, mid.toInt())
    }

    @Test
    fun `gate shortens the sample`() {
        val wav = sineWav(frames = 1000, amplitude = 0.5)
        val gated = Envelope.gate(wav, frames = 200, fadeMs = 0.0)
        assertEquals(200, gated.frameCount)
    }
}
