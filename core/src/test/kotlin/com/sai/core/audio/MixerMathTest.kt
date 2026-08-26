package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MixerMathTest {

    private val unity = MixerMath.Strip()
    private val strips = List(MixerMath.STRIP_COUNT) { unity }

    @Test
    fun `mixerTrack 1 through 8 map onto strips 0 through 7`() {
        assertNull(MixerMath.stripIndex(0))
        assertEquals(0, MixerMath.stripIndex(1))
        assertEquals(7, MixerMath.stripIndex(8))
        assertNull(MixerMath.stripIndex(9))
    }

    @Test
    fun `muted rack or master is silent`() {
        val channel = MixerMath.Channel(muted = true, mixerTrack = 1)
        assertFalse(MixerMath.isAudible(channel, strips, masterMuted = false))
        assertFalse(MixerMath.isAudible(MixerMath.Channel(mixerTrack = 1), strips, masterMuted = true))
    }

    @Test
    fun `solo isolates other strips and unassigned channels`() {
        val soloed = strips.mapIndexed { i, strip -> if (i == 0) strip.copy(soloed = true) else strip }
        assertTrue(MixerMath.isAudible(MixerMath.Channel(mixerTrack = 1), soloed, false))
        assertFalse(MixerMath.isAudible(MixerMath.Channel(mixerTrack = 2), soloed, false))
        assertFalse(MixerMath.isAudible(MixerMath.Channel(mixerTrack = 0), soloed, false))
    }

    @Test
    fun `rack solo silences non-soloed channels`() {
        val open = MixerMath.Channel(soloed = true, mixerTrack = 1)
        val closed = MixerMath.Channel(soloed = false, mixerTrack = 2)
        assertTrue(MixerMath.isAudible(open, strips, false, anyRackSolo = true))
        assertFalse(MixerMath.isAudible(closed, strips, false, anyRackSolo = true))
        assertTrue(MixerMath.isAudible(closed, strips, false, anyRackSolo = false))
    }

    @Test
    fun `linear gain multiplies every fader`() {
        val gain = MixerMath.linearGain(
            stepVolume = 127,
            rackVolume = 0.5f,
            stripVolume = 0.5f,
            mixerMaster = 1f,
            projectMaster = 1f,
        )
        assertEquals(0.25f, gain)
    }
}
