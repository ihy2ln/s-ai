package com.sai.app

import android.content.Context
import com.sai.core.audio.RackMix
import com.sai.core.audio.StereoShaper
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import com.sai.core.tracker.TrackerEngine
import kotlin.math.abs

class Sequencer(
    private val context: Context,
    private val instrumentsById: Map<Int, SampleEntry>,
    /** When true, each of the 8 tracker channels is monophonic during playback: a new step on a
     *  track immediately cuts off whatever that same track was still playing (Cut Itself).
     *  Live-updatable so MONO/POLY takes effect without restarting play. */
    chokeSameTrack: Boolean = false,
) {
    @Volatile var chokeSameTrack: Boolean = chokeSameTrack

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
        for (id in usedInstruments) {
            val entry = instrumentsById[id] ?: continue
            try {
                val bytes = resolver.openInputStream(entry.uri)!!.use { it.readBytes() }
                sampleCache[id] = try {
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
        val rack = ChannelRackStore.channel(context, track)
        if (rack != null && !RackMix.shouldPlay(rack.muted)) return

        val note = step.note ?: ROOT_NOTE
        val rate = ProjectPlayback.rateForNote(context, note, ROOT_NOTE)
        val mixedVolume = RackMix.combinedStepVolume(step.volume ?: 127, rack?.volume ?: 1f)
        if (mixedVolume <= 0) return
        var processed = com.sai.core.audio.SampleEditor.gain(wav, ProjectPlayback.gainDb(context, mixedVolume))

        val pan = RackMix.shaperPan(rack?.pan ?: 0.5f)
        if (abs(pan) > 0.02) {
            processed = StereoShaper.apply(processed, pan, 1.0, 0.0)
        }

        val chokeGroup = if (chokeSameTrack) "tracker-track-$track" else null
        AudioPlayback.playOneShot(processed, rate, context, chokeGroup)
    }

    companion object {
        private const val ROOT_NOTE = 60
    }
}
