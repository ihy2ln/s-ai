package com.sai.core.audio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WavIOTest {

    @Test
    fun `round trips a mono 16-bit PCM WAV through a file`() {
        val original = sineWav(sampleRate = 44100, channels = 1, frames = 2000)
        val file = File.createTempFile("sai-test", ".wav")
        try {
            WavIO.write(original, file)
            assertTrue(file.length() > 44)
            assertEquals(original, WavIO.read(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `round trips a stereo 16-bit PCM WAV`() {
        val original = sineWav(sampleRate = 48000, channels = 2, frames = 1000, freqHz = 220.0)
        val bytesOut = java.io.ByteArrayOutputStream()
        WavIO.write(original, bytesOut)
        assertEquals(original, WavIO.read(bytesOut.toByteArray()))
    }

    @Test
    fun `rejects non-16-bit PCM`() {
        val bytes = WavIOTestFixtures.eightBitPcmWav()
        assertThrowsMessageContaining("16-bit") { WavIO.read(bytes) }
    }
}

private object WavIOTestFixtures {
    fun eightBitPcmWav(): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(44 + 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + 4)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(1)
        buf.putInt(44100)
        buf.putInt(44100)
        buf.putShort(1)
        buf.putShort(8)
        buf.put("data".toByteArray())
        buf.putInt(4)
        buf.put(byteArrayOf(1, 2, 3, 4))
        return buf.array()
    }
}

private fun assertThrowsMessageContaining(fragment: String, block: () -> Unit) {
    try {
        block()
        throw AssertionError("Expected an exception containing '$fragment' but none was thrown")
    } catch (e: IllegalArgumentException) {
        assertTrue(e.message.orEmpty().contains(fragment), "Expected message to contain '$fragment', was: ${e.message}")
    }
}
