package com.sai.app

import android.content.Context
import com.sai.core.audio.MixerMath
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
                val playedPosition = engine.songPosition
                val playedStep = engine.stepIndex
                val events = engine.advance()
                onPositionChanged?.invoke(playedPosition, playedStep)
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
        val channel = MixerMath.Channel(
            muted = rack?.muted ?: false,
            volume = rack?.volume ?: 1f,
            pan = rack?.pan ?: 0.5f,
            mixerTrack = rack?.mixerTrack ?: 0,
        )
        val strips = MixerStore.mathStrips(context)
        if (!MixerMath.isAudible(channel, strips, MixerStore.masterMuted(context))) return

        val note = step.note ?: ROOT_NOTE
        val rate = ProjectPlayback.rateForNote(context, note, ROOT_NOTE)
        val linear = MixerMath.linearGain(
            stepVolume = step.volume ?: 127,
            rackVolume = channel.volume,
            stripVolume = MixerMath.stripVolume(channel, strips),
            mixerMaster = MixerStore.masterVolume(context),
            projectMaster = ProjectPlayback.masterVolume(context) / 127f,
        )
        if (linear <= 0f) return
        var processed = com.sai.core.audio.SampleEditor.gain(wav, MixerMath.gainDb(linear))

        val pan = RackMix.shaperPan(channel.pan)
        if (abs(pan) > 0.02) {
            processed = StereoShaper.apply(processed, pan, 1.0, 0.0)
        }

        MixerStore.hit(MixerMath.stripIndex(channel.mixerTrack), linear)
        val chokeGroup = if (chokeSameTrack) "tracker-track-$track" else null
        AudioPlayback.playOneShot(processed, rate, context, chokeGroup)
    }

    companion object {
        private const val ROOT_NOTE = 60
    }
}
