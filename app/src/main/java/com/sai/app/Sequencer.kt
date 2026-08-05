package com.sai.app

import android.content.Context
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import com.sai.core.tracker.TrackerEngine
import kotlin.math.log10
import kotlin.math.pow

class Sequencer(
    private val context: Context,
    private val instruments: List<SampleEntry>,
    /** When true, each of the 8 tracker channels is monophonic during playback: a new step on a
     *  track immediately cuts off whatever that same track was still playing (Cut Itself). */
    private val chokeSameTrack: Boolean = false,
) {
    private val resolver = context.contentResolver
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
                    playOneShot(wav, event.step, event.track)
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

    private fun playOneShot(wav: Wav, step: Step, track: Int) {
        val note = step.note ?: ROOT_NOTE
        val rate = 2.0.pow((note - ROOT_NOTE) / 12.0).toFloat()

        val volume = step.volume ?: 127
        val gainDb = if (volume <= 0) -80.0 else 20.0 * log10(volume / 127.0)
        val gained = SampleEditor.gain(wav, gainDb)

        val chokeGroup = if (chokeSameTrack) "tracker-track-$track" else null
        AudioPlayback.playOneShot(gained, rate, context, chokeGroup)
    }

    companion object {
        private const val ROOT_NOTE = 60
    }
}
