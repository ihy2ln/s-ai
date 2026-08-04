package com.sai.core.audio

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleEditorTest {

    @Test
    fun `trim, gain, reverse and normalize alter audio and the edit survives a save-reload round trip`() {
        val original = sineWav(sampleRate = 44100, channels = 1, frames = 4410, freqHz = 440.0, amplitude = 0.5)

        val trimmed = SampleEditor.trim(original, startFrame = 1000, endFrame = 3000)
        assertEquals(2000, trimmed.frameCount)

        val quieter = SampleEditor.gain(trimmed, gainDb = -6.0)
        val trimmedPeak = trimmed.samples.maxOf { abs(it.toInt()) }
        val quieterPeak = quieter.samples.maxOf { abs(it.toInt()) }
        assertTrue(quieterPeak < trimmedPeak, "gain(-6dB) should reduce peak amplitude")

        val reversed = SampleEditor.reverse(quieter)
        assertEquals(quieter.samples.last(), reversed.samples.first())
        assertEquals(quieter.samples.first(), reversed.samples.last())

        val normalized = SampleEditor.normalize(reversed, targetPeak = Short.MAX_VALUE.toDouble())
        val normalizedPeak = normalized.samples.maxOf { abs(it.toInt()) }
        assertTrue(normalizedPeak >= Short.MAX_VALUE - 1, "normalize should bring peak back up to full scale")

        val savedFile = File.createTempFile("sai-edited-sample", ".wav")
        try {
            WavIO.write(normalized, savedFile)
            assertTrue(savedFile.length() > 44, "saved file should contain audio data, not just a header")

            val reloaded = WavIO.read(savedFile)
            assertEquals(normalized, reloaded, "reloading the saved file should reproduce the edited audio exactly")
            assertEquals(2000, reloaded.frameCount)
            assertEquals(original.sampleRate, reloaded.sampleRate)
        } finally {
            savedFile.delete()
        }
    }

    @Test
    fun `normalize is a no-op on silence`() {
        val silence = Wav(sampleRate = 44100, channels = 1, samples = ShortArray(100))
        assertEquals(silence, SampleEditor.normalize(silence))
    }

    @Test
    fun `gain clamps instead of wrapping on overflow`() {
        val loud = Wav(sampleRate = 44100, channels = 1, samples = shortArrayOf(30000, -30000))
        val boosted = SampleEditor.gain(loud, gainDb = 12.0)
        assertEquals(Short.MAX_VALUE, boosted.samples[0])
        assertEquals(Short.MIN_VALUE, boosted.samples[1])
    }
}
