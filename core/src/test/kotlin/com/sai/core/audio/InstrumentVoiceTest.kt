package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertTrue

class InstrumentVoiceTest {

    @Test
    fun `every voice kind renders a non-silent buffer`() {
        for (kind in VoiceKind.entries) {
            val wav = InstrumentVoice.render(kind, midiNote = 60)
            assertTrue(wav.frameCount > 100, kind.name)
            assertTrue(wav.samples.any { it != 0.toShort() }, kind.name)
        }
    }

    @Test
    fun `higher notes are shorter wavelength than lower notes`() {
        val low = InstrumentVoice.render(VoiceKind.PULSE_KEYS, midiNote = 40)
        val high = InstrumentVoice.render(VoiceKind.PULSE_KEYS, midiNote = 80)
        assertTrue(low.frameCount > 0 && high.frameCount > 0)
        assertTrue(InstrumentVoice.midiToHz(80) > InstrumentVoice.midiToHz(40))
    }

    @Test
    fun `home module names map to voices`() {
        assertTrue(InstrumentVoice.kindForHomeModule("PULSE_KEYS") == VoiceKind.PULSE_KEYS)
        assertTrue(InstrumentVoice.kindForHomeModule("SAMPLER") == null)
    }
}
