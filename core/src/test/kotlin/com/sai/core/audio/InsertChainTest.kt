package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsertChainTest {

    @Test
    fun `empty and bypassed chains are inactive`() {
        assertTrue(!InsertChain().isActive)
        val bypassed = InsertChain.of(
            InsertSlot(InsertKind.DELAY, bypassed = true, params = InsertFx.defaults(InsertKind.DELAY)),
        )
        assertTrue(!bypassed.isActive)
        val wav = sineWav(frames = 200, amplitude = 0.4)
        assertTrue(wav.samples.contentEquals(InsertFx.apply(wav, bypassed).samples))
    }

    @Test
    fun `plus withSlot without and moved keep order`() {
        val delay = InsertSlot(InsertKind.DELAY, params = InsertFx.defaults(InsertKind.DELAY), engineId = "vst3.delay")
        val reverb = InsertSlot(InsertKind.REVERB, params = InsertFx.defaults(InsertKind.REVERB), engineId = "vst2.reverb")
        val chain = InsertChain().plus(delay).plus(reverb)
        assertEquals(listOf(InsertKind.DELAY, InsertKind.REVERB), chain.slots.map { it.kind })
        val swapped = chain.moved(1, -1)
        assertEquals(listOf(InsertKind.REVERB, InsertKind.DELAY), swapped.slots.map { it.kind })
        val trimmed = swapped.without(0)
        assertEquals(listOf(InsertKind.DELAY), trimmed.slots.map { it.kind })
        val replaced = trimmed.withSlot(0, reverb)
        assertEquals(InsertKind.REVERB, replaced.slots[0].kind)
    }

    @Test
    fun `order hints catch delay after reverb and tune after eq`() {
        assertEquals(
            "Put Delay before Reverb",
            InsertChain.of(
                InsertSlot(InsertKind.REVERB, params = InsertFx.defaults(InsertKind.REVERB)),
                InsertSlot(InsertKind.DELAY, params = InsertFx.defaults(InsertKind.DELAY)),
            ).orderHint(),
        )
        assertEquals(
            "Put Tune before EQ",
            InsertChain.of(
                InsertSlot(InsertKind.EQUALIZER, params = InsertFx.defaults(InsertKind.EQUALIZER)),
                InsertSlot(InsertKind.TUNE, params = InsertFx.defaults(InsertKind.TUNE)),
            ).orderHint(),
        )
        assertNull(
            InsertChain.of(
                InsertSlot(InsertKind.DELAY, params = InsertFx.defaults(InsertKind.DELAY)),
                InsertSlot(InsertKind.REVERB, params = InsertFx.defaults(InsertKind.REVERB)),
            ).orderHint(),
        )
    }

    @Test
    fun `applying a chain runs every live slot`() {
        val wav = sineWav(frames = 1500, amplitude = 0.5, freqHz = 220.0)
        val delay = InsertSlot(InsertKind.DELAY, params = InsertFx.defaults(InsertKind.DELAY), engineId = "vst3.delay")
        val crush = InsertSlot(InsertKind.CRUSH, params = InsertFx.defaults(InsertKind.CRUSH), engineId = "vst3.crush")
        val chained = InsertFx.apply(wav, InsertChain.of(delay, crush))
        val onlyDelay = InsertFx.apply(wav, delay)
        assertTrue(!chained.samples.contentEquals(onlyDelay.samples))
        assertTrue(chained.frameCount >= wav.frameCount)
    }

    @Test
    fun `legacy single insert still drives stripChain`() {
        val insert = InsertSlot(InsertKind.TAPE, params = InsertFx.defaults(InsertKind.TAPE))
        val strips = List(MixerMath.STRIP_COUNT) { i ->
            MixerMath.Strip(insert = if (i == 0) insert else InsertSlot())
        }
        assertTrue(MixerMath.stripChain(MixerMath.Channel(mixerTrack = 1), strips).isActive)
        assertTrue(!MixerMath.stripChain(MixerMath.Channel(mixerTrack = 2), strips).isActive)
    }

    @Test
    fun `kindForEngine maps aliases and one-knobs`() {
        assertEquals(InsertKind.DISTORTION, InsertFx.kindForEngine("vst2.drive"))
        assertEquals(InsertKind.DELAY, InsertFx.kindForEngine("vst3.slapback"))
        assertEquals(InsertKind.TAPE, InsertFx.kindForEngine("knob.toasty"))
        assertEquals(InsertKind.EQUALIZER, InsertFx.kindForEngine("knob.brighter"))
        assertEquals(InsertKind.COMPRESSOR, InsertFx.kindForEngine("vst3.duck"))
        assertEquals(InsertKind.TUNE, InsertFx.kindForEngine("vst3.tune"))
    }
}
