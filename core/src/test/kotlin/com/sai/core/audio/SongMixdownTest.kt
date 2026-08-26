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

    private fun render(channel: MixerMath.Channel, strips: List<MixerMath.Strip>): Wav {
        val steps = MutableList(Phrase.STEP_COUNT) { Step() }
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
            sampleRate = 44100,
        )
    }
}
