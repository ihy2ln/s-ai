package com.sai.core.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackerEngineTest {

    private fun phraseWithNotesAt(vararg indexedNotes: Pair<Int, Int>): Phrase {
        val steps = MutableList(Phrase.MAX_STEPS) { Step() }
        for ((index, note) in indexedNotes) steps[index] = Step(note = note, instrument = 0)
        return Phrase(steps)
    }

    @Test
    fun `emits trigger events only for non-empty steps, per track`() {
        val kick = phraseWithNotesAt(0 to 36, 4 to 36, 8 to 36, 12 to 36)
        val hat = phraseWithNotesAt(0 to 42, 2 to 42, 4 to 42, 6 to 42, 8 to 42, 10 to 42, 12 to 42, 14 to 42)

        val song = Song(positions = listOf(listOf(1, 2, null, null, null, null, null, null)))
        val engine = TrackerEngine(song, mapOf(1 to kick, 2 to hat))

        val step0 = engine.advance()
        assertEquals(2, step0.size)
        assertEquals(0, step0[0].track)
        assertEquals(36, step0[0].step.note)
        assertEquals(1, step0[1].track)
        assertEquals(42, step0[1].step.note)

        val step1 = engine.advance()
        assertTrue(step1.isEmpty(), "step 1 has no notes on either track")

        val step2 = engine.advance()
        assertEquals(1, step2.size)
        assertEquals(1, step2[0].track)
    }

    @Test
    fun `wraps from the last step of a phrase back to step zero on the next song position`() {
        val song = Song(positions = listOf(listOf(1, null, null, null, null, null, null, null), listOf(2, null, null, null, null, null, null, null)))
        val phraseA = phraseWithNotesAt(15 to 60)
        val phraseB = phraseWithNotesAt(0 to 62)
        val engine = TrackerEngine(song, mapOf(1 to phraseA, 2 to phraseB))

        repeat(15) { engine.advance() }
        assertEquals(0, engine.songPosition)
        assertEquals(15, engine.stepIndex)

        val lastStepOfPhraseA = engine.advance()
        assertEquals(1, engine.songPosition)
        assertEquals(0, engine.stepIndex)
        assertEquals(60, lastStepOfPhraseA.single().step.note)

        val firstStepOfPhraseB = engine.advance()
        assertEquals(62, firstStepOfPhraseB.single().step.note)
    }

    @Test
    fun `loops back to song position zero after the last position`() {
        val song = Song(positions = listOf(listOf(1, null, null, null, null, null, null, null)))
        val phrase = phraseWithNotesAt(15 to 60)
        val engine = TrackerEngine(song, mapOf(1 to phrase))

        repeat(16) { engine.advance() }
        assertEquals(0, engine.songPosition)
        assertEquals(0, engine.stepIndex)
    }

    @Test
    fun `an unassigned track slot never emits events`() {
        val song = Song(positions = listOf(listOf(null, null, null, null, null, null, null, null)))
        val engine = TrackerEngine(song, emptyMap())

        repeat(32) {
            assertTrue(engine.advance().isEmpty())
        }
    }

    @Test
    fun `reset returns to the start of the loop`() {
        val song = Song(positions = listOf(listOf(1, null, null, null, null, null, null, null)))
        val engine = TrackerEngine(song, mapOf(1 to Phrase.empty()))

        repeat(20) { engine.advance() }
        engine.reset()

        assertEquals(0, engine.songPosition)
        assertEquals(0, engine.stepIndex)
    }

    @Test
    fun `eight-step pattern wraps after step 7`() {
        val song = Song(positions = listOf(
            listOf(1, null, null, null, null, null, null, null),
            listOf(2, null, null, null, null, null, null, null),
        ))
        val phraseA = phraseWithNotesAt(7 to 60)
        val phraseB = phraseWithNotesAt(0 to 62)
        val engine = TrackerEngine(song, mapOf(1 to phraseA, 2 to phraseB), patternLengthAt = { 8 })

        repeat(7) { engine.advance() }
        assertEquals(0, engine.songPosition)
        assertEquals(7, engine.stepIndex)

        val last = engine.advance()
        assertEquals(60, last.single().step.note)
        assertEquals(1, engine.songPosition)
        assertEquals(0, engine.stepIndex)
    }

    @Test
    fun `loop range stays inside the selected song rows`() {
        val rows = listOf(
            listOf(1, null, null, null, null, null, null, null),
            listOf(2, null, null, null, null, null, null, null),
            listOf(3, null, null, null, null, null, null, null),
        )
        val song = Song(positions = rows)
        val engine = TrackerEngine(
            song,
            mapOf(
                1 to phraseWithNotesAt(0 to 40),
                2 to phraseWithNotesAt(0 to 50),
                3 to phraseWithNotesAt(0 to 60),
            ),
            patternLengthAt = { 8 },
            loopStart = 1,
            loopEnd = 2,
        )

        assertEquals(1, engine.songPosition)
        val first = engine.advance()
        assertEquals(50, first.single().step.note)

        repeat(7) { engine.advance() }
        assertEquals(2, engine.songPosition)
        val second = engine.advance()
        assertEquals(60, second.single().step.note)

        repeat(7) { engine.advance() }
        assertEquals(1, engine.songPosition)
        val wrapped = engine.advance()
        assertEquals(50, wrapped.single().step.note)
    }

    @Test
    fun `thirty-two step pattern does not wrap at 16`() {
        val song = Song(positions = listOf(listOf(1, null, null, null, null, null, null, null)))
        val phrase = phraseWithNotesAt(16 to 70, 31 to 72)
        val engine = TrackerEngine(song, mapOf(1 to phrase), patternLengthAt = { 32 })

        repeat(16) { engine.advance() }
        assertEquals(0, engine.songPosition)
        assertEquals(16, engine.stepIndex)
        val mid = engine.advance()
        assertEquals(70, mid.single().step.note)

        repeat(14) { engine.advance() }
        val last = engine.advance()
        assertEquals(72, last.single().step.note)
        assertEquals(0, engine.songPosition)
        assertEquals(0, engine.stepIndex)
    }
}
