package com.sai.core.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrangementTest {

    private fun phraseAt(step: Int, note: Int = 60, instrument: Int = 1): Phrase {
        val steps = MutableList(Phrase.MAX_STEPS) { Step() }
        steps[step] = Step(note = note, instrument = instrument, volume = 127)
        return Phrase(steps)
    }

    @Test
    fun `empty playlist walks the song grid`() {
        val song = Song(positions = listOf(
            listOf(1, null, null, null, null, null, null, null),
            listOf(2, null, null, null, null, null, null, null),
        ))
        val phrases = mapOf(1 to phraseAt(0, 36), 2 to phraseAt(0, 48))
        val length = { _: Int -> 8 }
        assertFalse(Arrangement.usesPatternClips(emptyList()))
        assertEquals(16, Arrangement.totalSteps(emptyList(), song, length))
        val first = Arrangement.patternEventsAt(emptyList(), song, phrases, 0, length)
        assertEquals(1, first.size)
        assertEquals(36, first[0].step.note)
        val secondPattern = Arrangement.patternEventsAt(emptyList(), song, phrases, 8, length)
        assertEquals(48, secondPattern[0].step.note)
    }

    @Test
    fun `pattern clips replace sequential walk and can layer`() {
        val song = Song(positions = listOf(
            listOf(1, null, null, null, null, null, null, null),
            listOf(2, null, null, null, null, null, null, null),
        ))
        val phrases = mapOf(1 to phraseAt(0, 36), 2 to phraseAt(0, 48))
        val clips = listOf(
            PlaylistClip(1, ClipKind.PATTERN, 0, 0, 8, pattern = 0),
            PlaylistClip(2, ClipKind.PATTERN, 1, 0, 8, pattern = 1),
        )
        val length = { _: Int -> 8 }
        assertTrue(Arrangement.usesPatternClips(clips))
        assertEquals(8, Arrangement.totalSteps(clips, song, length))
        val events = Arrangement.patternEventsAt(clips, song, phrases, 0, length)
        assertEquals(setOf(36, 48), events.map { it.step.note }.toSet())
    }

    @Test
    fun `audio clips fire only on their start step`() {
        val clip = PlaylistClip(1, ClipKind.AUDIO, 0, 4, 16, sampleId = 9)
        assertEquals(1, Arrangement.audioStartingAt(listOf(clip), 4).size)
        assertTrue(Arrangement.audioStartingAt(listOf(clip), 5).isEmpty())
        assertTrue(Arrangement.audioStartingAt(listOf(clip.copy(muted = true)), 4).isEmpty())
    }

    @Test
    fun `pattern loop covers that pattern's sequential span`() {
        val song = Song(positions = List(3) { List(Song.TRACK_COUNT) { null } })
        val range = Arrangement.loopRange(
            clips = emptyList(),
            song = song,
            patternLengthAt = { 8 },
            mode = LoopMode.PATTERN,
            currentPattern = 1,
            loopStart = 0,
            loopEnd = 2,
        )
        assertEquals(8, range.first)
        assertEquals(15, range.last)
    }
}
