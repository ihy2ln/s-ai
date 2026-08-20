package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OscillatorTest {

    @Test
    fun `generates expected frame count and channel layout`() {
        val wav = Oscillator.generate(Waveform.SAW, sampleRate = 44100, channels = 2, durationSec = 0.5)
        assertEquals(22050, wav.frameCount)
        assertEquals(2, wav.channels)
        assertEquals(44100, wav.samples.size)
    }

    @Test
    fun `all waveforms stay within 16-bit range`() {
        for (waveform in Waveform.entries) {
            val wav = Oscillator.generate(waveform, amplitude = 0.99)
            assertTrue(wav.samples.all { it >= Short.MIN_VALUE && it <= Short.MAX_VALUE }, waveform.name)
        }
    }

    @Test
    fun `sine matches existing test helper`() {
        val fromOscillator = Oscillator.generate(Waveform.SINE, frames = 100)
        val fromHelper = sineWav(frames = 100)
        assertEquals(fromHelper.samples.size, fromOscillator.samples.size)
        for (i in fromHelper.samples.indices) {
            assertEquals(fromHelper.samples[i], fromOscillator.samples[i])
        }
    }

    private fun Oscillator.generate(
        waveform: Waveform,
        sampleRate: Int = 44100,
        channels: Int = 1,
        frames: Int,
        freqHz: Double = 440.0,
        amplitude: Double = 0.5,
    ): Wav = generate(waveform, sampleRate, channels, frames.toDouble() / sampleRate, freqHz, amplitude)
}
