package com.sai.core.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteNamesTest {
    @Test
    fun `formats MIDI note numbers as tracker-style note names`() {
        assertEquals("C-4", NoteNames.format(60))
        assertEquals("C#4", NoteNames.format(61))
        assertEquals("A-4", NoteNames.format(69))
        assertEquals("C-0", NoteNames.format(12))
        assertEquals("B-3", NoteNames.format(59))
    }
}
