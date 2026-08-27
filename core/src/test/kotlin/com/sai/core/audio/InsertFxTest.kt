package com.sai.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsertFxTest {

    @Test
    fun `inactive slot is a no-op`() {
        val wav = sineWav(frames = 256, amplitude = 0.4)
        assertTrue(wav.samples.contentEquals(InsertFx.apply(wav, InsertSlot()).samples))
        val bypassed = InsertSlot(
            kind = InsertKind.REVERB,
            bypassed = true,
            params = InsertFx.defaults(InsertKind.REVERB),
        )
        assertTrue(wav.samples.contentEquals(InsertFx.apply(wav, bypassed).samples))
        assertFalse(bypassed.isActive)
        assertEquals("off", bypassed.fingerprint())
    }

    @Test
    fun `compressor makeup raises peak`() {
        val wav = sineWav(frames = 4000, amplitude = 0.2, freqHz = 220.0)
        val slot = InsertSlot(
            kind = InsertKind.COMPRESSOR,
            params = InsertFx.mergeDefaults(
                InsertKind.COMPRESSOR,
                mapOf("ratio" to 1.0, "makeup" to 12.0),
            ),
        )
        val wet = InsertFx.apply(wav, slot)
        val dryPeak = wav.samples.maxOf { abs(it.toInt()) }
        val wetPeak = wet.samples.maxOf { abs(it.toInt()) }
        assertTrue(wetPeak > dryPeak, "makeup gain should raise the peak")
    }

    @Test
    fun `reverb insert pads a tail`() {
        val wav = sineWav(frames = 800, amplitude = 0.5)
        val slot = InsertSlot(
            kind = InsertKind.REVERB,
            params = InsertFx.defaults(InsertKind.REVERB),
        )
        val wet = InsertFx.apply(wav, slot)
        assertTrue(wet.frameCount > wav.frameCount)
        assertTrue(InsertFx.tailFrames(slot, wav.sampleRate) > 0)
    }

    @Test
    fun `short labels match kind`() {
        assertEquals("fx", InsertSlot().shortLabel())
        assertEquals("RV", InsertSlot(InsertKind.REVERB, params = InsertFx.defaults(InsertKind.REVERB)).shortLabel())
        assertEquals(
            "eq",
            InsertSlot(InsertKind.EQUALIZER, bypassed = true, params = InsertFx.defaults(InsertKind.EQUALIZER)).shortLabel(),
        )
        assertEquals("DL", InsertSlot(InsertKind.DELAY, params = InsertFx.defaults(InsertKind.DELAY)).shortLabel())
        assertEquals("DS", InsertSlot(InsertKind.DISTORTION, params = InsertFx.defaults(InsertKind.DISTORTION)).shortLabel())
    }

    @Test
    fun `delay insert pads a tail`() {
        val wav = sineWav(frames = 600, amplitude = 0.5)
        val slot = InsertSlot(InsertKind.DELAY, params = InsertFx.defaults(InsertKind.DELAY))
        val wet = InsertFx.apply(wav, slot)
        assertTrue(wet.frameCount > wav.frameCount)
    }
}
