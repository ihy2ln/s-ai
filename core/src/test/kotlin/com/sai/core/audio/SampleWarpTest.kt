package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleWarpTest {

    @Test
    fun `time stretch factor above 1 lengthens the sample`() {
        val wav = sineWav(frames = 4410)
        val stretched = SampleWarp.timeStretch(wav, 2.0)
        assertTrue(stretched.frameCount > wav.frameCount * 1.5, "factor 2.0 should roughly double the length")
    }

    @Test
    fun `time stretch factor below 1 shortens the sample`() {
        val wav = sineWav(frames = 4410)
        val stretched = SampleWarp.timeStretch(wav, 0.5)
        assertTrue(stretched.frameCount < wav.frameCount, "factor 0.5 should shorten the length")
    }

    @Test
    fun `time stretch is a no-op at factor 1`() {
        val wav = sineWav(frames = 2000)
        val result = SampleWarp.timeStretch(wav, 1.0)
        assertEquals(wav, result)
    }

    @Test
    fun `pitch shift preserves duration within a small tolerance`() {
        val wav = sineWav(frames = 4410)
        val shiftedUp = SampleWarp.pitchShift(wav, 12.0)
        val shiftedDown = SampleWarp.pitchShift(wav, -12.0)
        assertTrue(abs(shiftedUp.frameCount - wav.frameCount) < wav.frameCount / 10, "pitch shift should keep duration roughly unchanged")
        assertTrue(abs(shiftedDown.frameCount - wav.frameCount) < wav.frameCount / 10, "pitch shift should keep duration roughly unchanged")
    }

    @Test
    fun `pitch shift is a no-op at zero semitones`() {
        val wav = sineWav(frames = 2000)
        assertEquals(wav, SampleWarp.pitchShift(wav, 0.0))
    }

    @Test
    fun `bpm sync speeds up a loop moving to a faster tempo`() {
        val wav = sineWav(frames = 4410)
        val synced = SampleWarp.bpmSync(wav, sourceBpm = 60.0, targetBpm = 120.0)
        assertTrue(synced.frameCount < wav.frameCount, "moving from 60 to 120 bpm should shorten (speed up) the loop")
    }

    @Test
    fun `granulate preserves total frame count`() {
        val wav = sineWav(frames = 4410)
        val granulated = SampleWarp.granulate(wav, grainMs = 30.0, scatter = 0.8)
        assertEquals(wav.frameCount, granulated.frameCount)
        assertEquals(wav.channels, granulated.channels)
    }

    @Test
    fun `granulate with zero scatter reproduces the original order exactly`() {
        val wav = sineWav(frames = 4410)
        val untouched = SampleWarp.granulate(wav, grainMs = 30.0, scatter = 0.0)
        assertEquals(wav, untouched)
    }

    @Test
    fun `granulate with full scatter reorders the grains`() {
        // Give each grain a distinct, easily-checked value so shuffling is verifiable.
        val sampleRate = 44100
        val grainMs = 20.0
        val grainFrames = (sampleRate * grainMs / 1000.0).toInt()
        val grainCount = 20
        val samples = ShortArray(grainFrames * grainCount)
        for (grain in 0 until grainCount) {
            for (i in 0 until grainFrames) {
                samples[grain * grainFrames + i] = (grain * 1000).toShort()
            }
        }
        val wav = Wav(sampleRate, 1, samples)
        val scattered = SampleWarp.granulate(wav, grainMs = grainMs, scatter = 1.0, seed = 42L)

        val originalLeadValues = (0 until grainCount).map { wav.samples[it * grainFrames] }
        val scatteredLeadValues = (0 until grainCount).map { scattered.samples[it * grainFrames] }
        assertTrue(originalLeadValues != scatteredLeadValues, "full scatter should change the grain order")
    }
}
