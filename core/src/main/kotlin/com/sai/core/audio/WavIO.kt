package com.sai.core.audio

import java.io.EOFException
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val PCM_FORMAT = 1
private const val HEADER_SIZE = 44

object WavIO {

    fun read(bytes: ByteArray): Wav {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        require(buf.remaining() >= 12) { "Not a WAV file: too short" }
        require(readTag(buf) == "RIFF") { "Not a WAV file: missing RIFF header" }
        buf.int
        require(readTag(buf) == "WAVE") { "Not a WAV file: missing WAVE tag" }

        var sampleRate = 0
        var channels = 0
        var samples: ShortArray? = null

        // Chunks are word-aligned and may include ones we don't care about (LIST, fact, ...);
        // skip anything we don't recognize by its declared size rather than assuming layout.
        while (buf.remaining() >= 8) {
            val id = readTag(buf)
            val size = buf.int
            val chunkStart = buf.position()
            when (id) {
                "fmt " -> {
                    val audioFormat = buf.short.toInt()
                    channels = buf.short.toInt()
                    sampleRate = buf.int
                    buf.int
                    buf.short
                    val bitsPerSample = buf.short.toInt()
                    require(audioFormat == PCM_FORMAT) {
                        "Only uncompressed PCM WAV is supported (found format $audioFormat)"
                    }
                    require(bitsPerSample == 16) {
                        "Only 16-bit PCM WAV is supported (found ${bitsPerSample}-bit)"
                    }
                }
                "data" -> {
                    val shortCount = size / 2
                    val out = ShortArray(shortCount)
                    for (i in 0 until shortCount) out[i] = buf.short
                    samples = out
                }
            }
            val consumed = buf.position() - chunkStart
            val remaining = size - consumed
            if (remaining > 0) buf.position(buf.position() + remaining)
            if (size % 2 == 1 && buf.remaining() > 0) buf.get()
        }

        requireNotNull(samples) { "WAV file has no data chunk" }
        require(channels > 0) { "WAV file has no fmt chunk" }
        return Wav(sampleRate, channels, samples)
    }

    fun read(file: File): Wav = read(file.readBytes())

    fun write(wav: Wav, out: OutputStream) {
        val bitsPerSample = 16
        val blockAlign = wav.channels * bitsPerSample / 8
        val byteRate = wav.sampleRate * blockAlign
        val dataSize = wav.samples.size * 2
        val chunkSize = 36 + dataSize

        val buf = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        writeTag(buf, "RIFF")
        buf.putInt(chunkSize)
        writeTag(buf, "WAVE")
        writeTag(buf, "fmt ")
        buf.putInt(16)
        buf.putShort(PCM_FORMAT.toShort())
        buf.putShort(wav.channels.toShort())
        buf.putInt(wav.sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(bitsPerSample.toShort())
        writeTag(buf, "data")
        buf.putInt(dataSize)
        for (s in wav.samples) buf.putShort(s)

        out.write(buf.array())
    }

    fun write(wav: Wav, file: File) = file.outputStream().use { write(wav, it) }

    private fun readTag(buf: ByteBuffer): String {
        if (buf.remaining() < 4) throw EOFException("Unexpected end of WAV data")
        val bytes = ByteArray(4)
        buf.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun writeTag(buf: ByteBuffer, tag: String) {
        buf.put(tag.toByteArray(Charsets.US_ASCII))
    }
}
