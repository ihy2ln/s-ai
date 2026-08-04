package com.sai.core.tracker

object NoteNames {
    private val NAMES = arrayOf("C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-")

    fun format(note: Int): String {
        val octave = note / 12 - 1
        val name = NAMES[((note % 12) + 12) % 12]
        return "$name$octave"
    }
}
