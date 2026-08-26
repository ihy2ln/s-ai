package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VocalFxTest {

    @Test
    fun `preserves frame count and sample rate`() {
        val wav = sineWav(frames = 2000, freqHz = 220.0)
        val treated = VocalFx.apply(wav)
        assertEquals(wav.frameCount, treated.frameCount)
        assertEquals(wav.sampleRate, treated.sampleRate)
        assertEquals(wav.channels, treated.channels)
    }

    @Test
    fun `silence stays silent`() {
        val silence = Wav(44100, 1, ShortArray(800))
        assertTrue(VocalFx.apply(silence).samples.all { it == 0.toShort() })
    }
}
