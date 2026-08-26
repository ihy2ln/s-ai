package com.sai.core.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class PhraseTest {

    @Test
    fun `fromSteps pads short phrases to MAX_STEPS`() {
        val phrase = Phrase.fromSteps(listOf(Step(note = 60, instrument = 1)))
        assertEquals(Phrase.MAX_STEPS, phrase.steps.size)
        assertEquals(60, phrase.steps[0].note)
        assertEquals(true, phrase.steps[1].isEmpty)
        assertEquals(true, phrase.steps.last().isEmpty)
    }

    @Test
    fun `coerceLength snaps to 8 16 or 32`() {
        assertEquals(8, Phrase.coerceLength(1))
        assertEquals(8, Phrase.coerceLength(8))
        assertEquals(16, Phrase.coerceLength(9))
        assertEquals(16, Phrase.coerceLength(16))
        assertEquals(32, Phrase.coerceLength(17))
        assertEquals(32, Phrase.coerceLength(99))
    }
}
