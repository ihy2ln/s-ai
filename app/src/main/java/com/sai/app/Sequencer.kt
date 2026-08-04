package com.sai.app

import android.content.ContentResolver
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import com.sai.core.tracker.TrackerEngine
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

class Sequencer(
    private val resolver: ContentResolver,
    private val instruments: List<SampleEntry>,
) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val sampleCache = mutableMapOf<Int, Wav>()

    var onPositionChanged: ((songPosition: Int, stepIndex: Int) -> Unit)? = null

    val isRunning: Boolean get() = running

    fun start(song: Song, phrases: Map<Int, Phrase>, bpm: Int) {
        stop()
        preloadSamples(phrases)

        val engine = TrackerEngine(song, phrases)
        val stepMillis = (60_000.0 / bpm / 4.0).toLong().coerceAtLeast(20)

        running = true
        thread = Thread {
            while (running) {
                val events = engine.advance()
                val position = engine.songPosition
                val step = engine.stepIndex
                onPositionChanged?.invoke(position, step)
                for (event in events) {
                    val instrument = event.step.instrument ?: continue
                    val wav = sampleCache[instrument] ?: continue
                    playOneShot(wav, event.step)
                }
                try {
                    Thread.sleep(stepMillis)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun preloadSamples(phrases: Map<Int, Phrase>) {
        sampleCache.clear()
        val usedInstruments = phrases.values.flatMap { it.steps }.mapNotNull { it.instrument }.toSet()
        for (index in usedInstruments) {
            val entry = instruments.getOrNull(index) ?: continue
            try {
                val bytes = resolver.openInputStream(entry.uri)!!.use { it.readBytes() }
                sampleCache[index] = try {
                    WavIO.read(bytes)
                } catch (wavError: Exception) {
                    AudioDecoder.decode(resolver, entry.uri)
                }
            } catch (e: Exception) {
                // Skip a sample that fails to decode rather than aborting the whole run.
            }
        }
    }

    private fun playOneShot(wav: Wav, step: Step) {
        val note = step.note ?: ROOT_NOTE
        val rate = 2.0.pow((note - ROOT_NOTE) / 12.0).toFloat()

        val volume = step.volume ?: 127
        val gainDb = if (volume <= 0) -80.0 else 20.0 * log10(volume / 127.0)
        val gained = SampleEditor.gain(wav, gainDb)

        val channelMask = if (gained.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val pcmBytes = ByteArray(gained.samples.size * 2)
        var i = 0
        for (s in gained.samples) {
            val v = s.toInt()
            pcmBytes[i++] = (v and 0xFF).toByte()
            pcmBytes[i++] = ((v shr 8) and 0xFF).toByte()
        }

        val minBufferSize = AudioTrack.getMinBufferSize(gained.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(gained.sampleRate)
                .setChannelMask(channelMask)
                .build(),
            max(minBufferSize, pcmBytes.size),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track.write(pcmBytes, 0, pcmBytes.size)
        if (rate != 1.0f) {
            try {
                track.playbackParams = PlaybackParams().setSpeed(rate).setPitch(rate)
            } catch (e: Exception) {
                // Some devices/formats reject a changed playback rate; fall back to unpitched playback.
            }
        }
        track.play()

        val durationMs = (gained.frameCount.toDouble() / gained.sampleRate / rate * 1000).toLong().coerceAtLeast(50)
        Thread {
            Thread.sleep(durationMs + 50)
            track.stop()
            track.release()
        }.apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val ROOT_NOTE = 60
    }
}
