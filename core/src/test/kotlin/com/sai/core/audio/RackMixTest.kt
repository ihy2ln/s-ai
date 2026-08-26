package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RackMixTest {

    @Test
    fun `muted channel is silent`() {
        assertFalse(RackMix.shouldPlay(muted = true))
        assertTrue(RackMix.shouldPlay(muted = false))
    }

    @Test
    fun `rack solo silences non-soloed rows`() {
        assertTrue(RackMix.isAudible(muted = false, soloed = true, anySolo = true))
        assertFalse(RackMix.isAudible(muted = false, soloed = false, anySolo = true))
        assertFalse(RackMix.isAudible(muted = true, soloed = true, anySolo = true))
        assertTrue(RackMix.isAudible(muted = false, soloed = false, anySolo = false))
    }

    @Test
    fun `rack volume scales step volume`() {
        assertEquals(127, RackMix.combinedStepVolume(127, 1f))
        assertEquals(50, RackMix.combinedStepVolume(100, 0.5f))
        assertEquals(0, RackMix.combinedStepVolume(127, 0f))
        assertEquals(99, RackMix.combinedStepVolume(127, 0.78f))
    }

    @Test
    fun `rack pan maps onto stereo shaper range`() {
        assertEquals(-1.0, RackMix.shaperPan(0f))
        assertEquals(0.0, RackMix.shaperPan(0.5f))
        assertEquals(1.0, RackMix.shaperPan(1f))
    }
}
