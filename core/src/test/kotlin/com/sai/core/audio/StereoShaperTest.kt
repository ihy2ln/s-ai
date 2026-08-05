package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StereoShaperTest {

    @Test
    fun `mono input is upmixed to stereo`() {
        val mono = sineWav(channels = 1, frames = 1000)
        val shaped = StereoShaper.apply(mono, pan = 0.0, width = 1.0, depth = 0.0)
        assertEquals(2, shaped.channels)
    }

    @Test
    fun `length is preserved when depth is zero`() {
        val wav = sineWav(channels = 2, frames = 2000)
        val shaped = StereoShaper.apply(wav, pan = 0.0, width = 1.0, depth = 0.0)
        assertEquals(wav.samples.size, shaped.samples.size)
    }

    @Test
    fun `depth adds length for the delay tail`() {
        val wav = sineWav(channels = 2, frames = 2000)
        val shaped = StereoShaper.apply(wav, pan = 0.0, width = 1.0, depth = 1.0)
        assertTrue(shaped.frameCount > wav.frameCount, "max depth should add a delayed tail")
    }

    @Test
    fun `silence stays silent`() {
        val silence = Wav(44100, 2, ShortArray(1000))
        val shaped = StereoShaper.apply(silence, pan = 0.5, width = 1.5, depth = 0.7)
        assertTrue(shaped.samples.all { it == 0.toShort() })
    }

    @Test
    fun `hard left pan silences the right channel`() {
        val wav = sineWav(channels = 2, frames = 2000, amplitude = 0.5)
        val shaped = StereoShaper.apply(wav, pan = -1.0, width = 1.0, depth = 0.0)
        val rightPeak = (0 until shaped.frameCount).maxOf { abs(shaped.samples[it * 2 + 1].toInt()) }
        assertTrue(rightPeak <= 1, "hard left pan should leave the right channel effectively silent")
    }

    @Test
    fun `zero width collapses left and right to the same signal`() {
        val wav = sineWav(channels = 2, frames = 2000, amplitude = 0.5)
        val shaped = StereoShaper.apply(wav, pan = 0.0, width = 0.0, depth = 0.0)
        for (i in 0 until shaped.frameCount) {
            assertEquals(shaped.samples[i * 2], shaped.samples[i * 2 + 1])
        }
    }
}
