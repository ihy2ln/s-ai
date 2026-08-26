package com.sai.core.audio

import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongMixdownTest {

    @Test
    fun `muted channel produces silence`() {
        val wav = render(
            channel = MixerMath.Channel(muted = true, mixerTrack = 1),
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
        )
        assertTrue(wav.samples.all { it == 0.toShort() })
    }

    @Test
    fun `audible hit writes stereo energy`() {
        val wav = render(
            channel = MixerMath.Channel(volume = 1f, pan = 0.5f, mixerTrack = 1),
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
        )
        assertEquals(2, wav.channels)
        assertTrue(wav.samples.any { abs(it.toInt()) > 100 })
    }

    @Test
    fun `hard-left pan leaves the right channel quiet`() {
        val wav = render(
            channel = MixerMath.Channel(volume = 1f, pan = 0f, mixerTrack = 1),
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
        )
        val rightPeak = (0 until wav.frameCount).maxOf { abs(wav.samples[it * 2 + 1].toInt()) }
        assertTrue(rightPeak <= 2, "hard left should silence the right channel")
    }

    @Test
    fun `eight-step pattern is shorter than sixteen`() {
        val eight = render(patternLength = 8)
        val sixteen = render(patternLength = 16)
        assertTrue(eight.frameCount < sixteen.frameCount)
    }

    @Test
    fun `swing delays the second hit`() {
        val steps = MutableList(Phrase.MAX_STEPS) { Step() }
        steps[0] = Step(note = 60, instrument = 0, volume = 127)
        steps[1] = Step(note = 60, instrument = 0, volume = 127)
        val phrase = Phrase(steps)
        val song = Song(positions = listOf(List(Song.TRACK_COUNT) { if (it == 0) 1 else null }))
        val click = sineWav(frames = 8, amplitude = 0.9)
        fun mix(swing: Int) = SongMixdown.render(
            song = song,
            phrases = mapOf(1 to phrase),
            bpm = 120,
            samplesById = mapOf(0 to click),
            channels = listOf(MixerMath.Channel(volume = 1f, mixerTrack = 1)),
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
            mixerMaster = 1f,
            masterMuted = false,
            projectMaster = 1f,
            pitchSemitones = 0,
            sampleRate = 44100,
            patternLengthAt = { 8 },
            swingPercent = swing,
        )
        val straight = mix(0)
        val swung = mix(100)
        val stepFrames = (44100 * 60.0 / 120.0 / 4.0).toInt()
        val straightOdd = (0 until 8).maxOf { abs(straight.samples[(stepFrames + it) * 2].toInt()) }
        val swungOnGrid = (0 until 8).maxOf { abs(swung.samples[(stepFrames + it) * 2].toInt()) }
        val swungDelayed = (0 until 8).maxOf { abs(swung.samples[(stepFrames + stepFrames / 2 + it) * 2].toInt()) }
        assertTrue(straightOdd > 100, "straight offbeat should land on the grid")
        assertTrue(swungOnGrid <= 2, "100% swing should leave the grid quiet")
        assertTrue(swungDelayed > 100, "100% swing should land halfway to the next even step")
    }

    @Test
    fun `rack solo silences non-soloed channels in the mixdown`() {
        val steps = MutableList(Phrase.MAX_STEPS) { Step() }
        steps[0] = Step(note = 60, instrument = 0, volume = 127)
        val phrase = Phrase(steps)
        val song = Song(positions = listOf(List(Song.TRACK_COUNT) { if (it == 0) 1 else null }))
        val click = sineWav(frames = 64, amplitude = 0.8)
        val wav = SongMixdown.render(
            song = song,
            phrases = mapOf(1 to phrase),
            bpm = 120,
            samplesById = mapOf(0 to click),
            channels = listOf(
                MixerMath.Channel(soloed = false, volume = 1f, mixerTrack = 1),
                MixerMath.Channel(soloed = true, mixerTrack = 2),
            ),
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
            mixerMaster = 1f,
            masterMuted = false,
            projectMaster = 1f,
            pitchSemitones = 0,
            sampleRate = 44100,
            patternLengthAt = { 8 },
        )
        assertTrue(wav.samples.all { it == 0.toShort() })
    }

    @Test
    fun `playlist audio clip lands in the mixdown`() {
        val clip = com.sai.core.tracker.PlaylistClip(
            id = 1,
            kind = com.sai.core.tracker.ClipKind.AUDIO,
            lane = 0,
            startStep = 2,
            lengthSteps = 4,
            sampleId = 0,
        )
        val click = sineWav(frames = 8, amplitude = 0.9)
        val wav = SongMixdown.render(
            song = Song.empty(),
            phrases = emptyMap(),
            bpm = 120,
            samplesById = mapOf(0 to click),
            channels = listOf(MixerMath.Channel(volume = 1f, mixerTrack = 1)),
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
            mixerMaster = 1f,
            masterMuted = false,
            projectMaster = 1f,
            pitchSemitones = 0,
            sampleRate = 44100,
            patternLengthAt = { 8 },
            clips = listOf(clip),
            audioOnly = true,
        )
        val stepFrames = (44100 * 60.0 / 120.0 / 4.0).toInt()
        val peakOnClip = (0 until 8).maxOf { abs(wav.samples[(stepFrames * 2 + it) * 2].toInt()) }
        val peakBefore = (0 until 8).maxOf { abs(wav.samples[it * 2].toInt()) }
        assertTrue(peakBefore <= 2, "audio clip should not start at step 0")
        assertTrue(peakOnClip > 100, "audio clip should sound at startStep")
    }

    @Test
    fun `stem of another track is silent`() {
        val steps = MutableList(Phrase.MAX_STEPS) { Step() }
        steps[0] = Step(note = 60, instrument = 0, volume = 127)
        val phrase = Phrase(steps)
        val song = Song(positions = listOf(List(Song.TRACK_COUNT) { if (it == 0) 1 else null }))
        val click = sineWav(frames = 64, amplitude = 0.8)
        val wav = SongMixdown.render(
            song = song,
            phrases = mapOf(1 to phrase),
            bpm = 120,
            samplesById = mapOf(0 to click),
            channels = listOf(MixerMath.Channel(volume = 1f, mixerTrack = 1)),
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
            mixerMaster = 1f,
            masterMuted = false,
            projectMaster = 1f,
            pitchSemitones = 0,
            sampleRate = 44100,
            patternLengthAt = { 8 },
            onlyTrack = 1,
        )
        assertTrue(wav.samples.all { it == 0.toShort() })
    }

    @Test
    fun `strip compressor makeup raises the mixdown peak`() {
        val dry = render()
        val insert = InsertSlot(
            kind = InsertKind.COMPRESSOR,
            params = InsertFx.mergeDefaults(InsertKind.COMPRESSOR, mapOf("ratio" to 1.0, "makeup" to 12.0)),
        )
        val wet = render(
            strips = List(MixerMath.STRIP_COUNT) { i ->
                MixerMath.Strip(insert = if (i == 0) insert else InsertSlot())
            },
        )
        val dryPeak = dry.samples.maxOf { abs(it.toInt()) }
        val wetPeak = wet.samples.maxOf { abs(it.toInt()) }
        assertTrue(wetPeak > dryPeak)
    }

    @Test
    fun `master insert makeup raises the mixdown peak`() {
        val dry = render()
        val insert = InsertSlot(
            kind = InsertKind.COMPRESSOR,
            params = InsertFx.mergeDefaults(InsertKind.COMPRESSOR, mapOf("ratio" to 1.0, "makeup" to 12.0)),
        )
        val wet = render(masterInsert = insert)
        val dryPeak = dry.samples.maxOf { abs(it.toInt()) }
        val wetPeak = wet.samples.maxOf { abs(it.toInt()) }
        assertTrue(wetPeak > dryPeak)
    }

    @Test
    fun `bypassed strip insert matches the dry mixdown`() {
        val dry = render()
        val insert = InsertSlot(
            kind = InsertKind.COMPRESSOR,
            bypassed = true,
            params = InsertFx.mergeDefaults(InsertKind.COMPRESSOR, mapOf("makeup" to 24.0)),
        )
        val wet = render(
            strips = List(MixerMath.STRIP_COUNT) { MixerMath.Strip(insert = insert) },
        )
        assertTrue(dry.samples.contentEquals(wet.samples))
    }

    private fun render(
        channel: MixerMath.Channel = MixerMath.Channel(volume = 1f, pan = 0.5f, mixerTrack = 1),
        strips: List<MixerMath.Strip> = List(MixerMath.STRIP_COUNT) { MixerMath.Strip() },
        patternLength: Int = Phrase.DEFAULT_LENGTH,
        masterInsert: InsertSlot = InsertSlot(),
    ): Wav {
        val steps = MutableList(Phrase.MAX_STEPS) { Step() }
        steps[0] = Step(note = 60, instrument = 0, volume = 127)
        val phrase = Phrase(steps)
        val song = Song(positions = listOf(List(Song.TRACK_COUNT) { if (it == 0) 1 else null }))
        val click = sineWav(frames = 64, amplitude = 0.8)
        return SongMixdown.render(
            song = song,
            phrases = mapOf(1 to phrase),
            bpm = 120,
            samplesById = mapOf(0 to click),
            channels = listOf(channel),
            strips = strips,
            mixerMaster = 1f,
            masterMuted = false,
            projectMaster = 1f,
            pitchSemitones = 0,
            masterInsert = masterInsert,
            sampleRate = 44100,
            patternLengthAt = { patternLength },
        )
    }
}
