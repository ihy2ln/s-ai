package com.sai.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.sai.core.audio.Wav
import kotlin.math.max

/** Records raw microphone audio into a mono 16-bit PCM Wav, for live-recording a sound straight
 *  into the sampler (mimicking a quick "record a take" workflow like Ableton's audio recording). */
class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private var thread: Thread? = null
    private val chunks = mutableListOf<ShortArray>()
    private var sampleRate = 44100

    val isRecording: Boolean get() = recording

    fun start(sampleRate: Int = 44100) {
        this.sampleRate = sampleRate
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = max(minBufferSize, 4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        audioRecord = recorder
        chunks.clear()
        recording = true
        recorder.startRecording()

        thread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (recording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    chunks.add(buffer.copyOf(read))
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop(skipMs: Int = 0): Wav {
        recording = false
        thread?.join(500)
        thread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val total = chunks.sumOf { it.size }
        val samples = ShortArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        chunks.clear()
        val skip = (sampleRate * skipMs.coerceAtLeast(0) / 1000.0).toInt().coerceIn(0, samples.size)
        val kept = if (skip <= 0) samples else samples.copyOfRange(skip, samples.size)
        return Wav(sampleRate, 1, kept)
    }
}
