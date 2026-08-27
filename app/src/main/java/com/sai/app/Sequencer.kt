package com.sai.app

import android.content.Context
import com.sai.core.audio.Envelope
import com.sai.core.audio.InsertFx
import com.sai.core.audio.MixerMath
import com.sai.core.audio.Oscillator
import com.sai.core.audio.RackMix
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.SampleWarp
import com.sai.core.audio.SequencerClock
import com.sai.core.audio.StereoShaper
import com.sai.core.audio.Swing
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import com.sai.core.audio.Waveform
import com.sai.core.tracker.Arrangement
import com.sai.core.tracker.LoopMode
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.PlaylistClip
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
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
    private val insertCache = mutableMapOf<String, Wav>()
    private val clickAccent: Wav = Oscillator.generate(
        Waveform.SQUARE,
        durationSec = 0.04,
        freqHz = 1200.0,
        amplitude = 0.32,
    )
    private val clickBeat: Wav = Oscillator.generate(
        Waveform.SQUARE,
        durationSec = 0.04,
        freqHz = 800.0,
        amplitude = 0.22,
    )

    var onPositionChanged: ((songPosition: Int, stepIndex: Int) -> Unit)? = null

    val isRunning: Boolean get() = running

    fun start(
        song: Song,
        phrases: Map<Int, Phrase>,
        bpm: Int,
        patternLengthAt: (Int) -> Int = { Phrase.DEFAULT_LENGTH },
        swingPercent: Int = 0,
        loopStart: Int = 0,
        loopEnd: Int = (song.positions.size - 1).coerceAtLeast(0),
        metronome: Boolean = false,
        countInBars: Int = 0,
        clips: List<PlaylistClip> = emptyList(),
        loopMode: LoopMode = LoopMode.SONG,
        currentPattern: Int = 0,
    ) {
        stop()
        preloadSamples(phrases, clips)

        val stepMillis = 60_000.0 / bpm.coerceAtLeast(1) / 4.0
        val loop = Arrangement.loopRange(
            clips, song, patternLengthAt, loopMode, currentPattern, loopStart, loopEnd,
        )

        running = true
        thread = Thread {
            val startNanos = System.nanoTime()
            var elapsedMs = 0.0
            if (countInBars > 0) {
                val beatMillis = stepMillis * 4.0
                val beats = countInBars.coerceAtLeast(1) * 4
                for (beat in 0 until beats) {
                    if (!running) return@Thread
                    onPositionChanged?.invoke(-1, beat)
                    ArrangementClock.set(-1, -1, beat)
                    playClick(accent = beat % 4 == 0)
                    elapsedMs += beatMillis
                    SequencerClock.waitUntil(SequencerClock.deadlineNanos(startNanos, elapsedMs)) { running }
                }
            }
            var globalStep = loop.first
            while (running) {
                val (position, localStep) = Arrangement.playhead(clips, song, patternLengthAt, globalStep)
                onPositionChanged?.invoke(position, localStep)
                ArrangementClock.set(globalStep, position, localStep)
                if (metronome && localStep % 4 == 0) {
                    playClick(accent = localStep == 0)
                }
                for (event in Arrangement.patternEventsAt(clips, song, phrases, globalStep, patternLengthAt)) {
                    val instrument = event.step.instrument ?: continue
                    val wav = sampleCache[instrument] ?: continue
                    playOneShot(wav, event.step, event.track, bpm)
                }
                for (clip in Arrangement.audioStartingAt(clips, globalStep)) {
                    val id = clip.sampleId ?: continue
                    val wav = sampleCache[id] ?: continue
                    playOneShot(
                        wav,
                        Step(note = 60, instrument = id, volume = 127, length = clip.length),
                        clip.lane.coerceIn(0, Arrangement.LANES - 1),
                        bpm,
                        pitched = false,
                    )
                }
                elapsedMs += stepMillis * Swing.intervalFraction(globalStep, swingPercent)
                SequencerClock.waitUntil(SequencerClock.deadlineNanos(startNanos, elapsedMs)) { running }
                globalStep++
                if (globalStep > loop.last) globalStep = loop.first
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

    private fun playClick(accent: Boolean) {
        AudioPlayback.playOneShot(if (accent) clickAccent else clickBeat, context = context, chokeGroup = "metronome")
    }

    private fun preloadSamples(phrases: Map<Int, Phrase>, clips: List<PlaylistClip> = emptyList()) {
        sampleCache.clear()
        insertCache.clear()
        val usedInstruments = phrases.values.flatMap { it.steps }.mapNotNull { it.instrument }.toSet() +
            clips.mapNotNull { it.sampleId }
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

    private fun playOneShot(wav: Wav, step: Step, track: Int, bpm: Int, pitched: Boolean = true) {
        val rack = ChannelRackStore.channel(context, track)
        val anyRackSolo = ChannelRackStore.anySolo(context)
        if (!RackMix.isAudible(rack?.muted ?: false, rack?.soloed ?: false, anyRackSolo)) return

        val channel = MixerMath.Channel(
            muted = rack?.muted ?: false,
            soloed = rack?.soloed ?: false,
            volume = rack?.volume ?: 1f,
            pan = rack?.pan ?: 0.5f,
            mixerTrack = rack?.mixerTrack ?: 0,
        )
        val strips = MixerStore.mathStrips(context)
        if (!MixerMath.isAudible(channel, strips, MixerStore.masterMuted(context), anyRackSolo)) return

        val note = step.note ?: ROOT_NOTE
        val rate = if (pitched) ProjectPlayback.rateForNote(context, note, ROOT_NOTE) else 1.0f
        val linear = MixerMath.linearGain(
            stepVolume = step.volume ?: 127,
            rackVolume = channel.volume,
            stripVolume = MixerMath.stripVolume(channel, strips),
            mixerMaster = MixerStore.masterVolume(context),
            projectMaster = ProjectPlayback.masterVolume(context) / 127f,
        )
        if (linear <= 0f) return

        val gateSteps = step.length?.takeIf { it > 0 }
        val gateFrames = if (gateSteps != null) {
            (wav.sampleRate * 60.0 / bpm.coerceAtLeast(1) / 4.0 * gateSteps).toInt().coerceAtLeast(1)
        } else {
            null
        }
        val stripChain = MixerMath.stripChain(channel, strips)
        val masterChain = MixerStore.masterChain(context)
        val cacheKey = "${step.instrument}|$pitched|$note|${gateFrames ?: 0}|${stripChain.fingerprint()}"
        var processed = insertCache.getOrPut(cacheKey) {
            var shaped = wav
            if (kotlin.math.abs(rate - 1.0f) > 0.0001f) {
                shaped = SampleWarp.resample(shaped, rate.toDouble())
            }
            if (gateFrames != null) {
                shaped = Envelope.gate(shaped, gateFrames)
            }
            InsertFx.apply(shaped, stripChain)
        }
        processed = SampleEditor.gain(processed, MixerMath.gainDb(linear))

        val pan = RackMix.shaperPan(channel.pan)
        if (abs(pan) > 0.02) {
            processed = StereoShaper.apply(processed, pan, 1.0, 0.0)
        }
        processed = InsertFx.apply(processed, masterChain)

        MixerStore.hit(MixerMath.stripIndex(channel.mixerTrack), linear)
        val chokeGroup = if (chokeSameTrack) "tracker-track-$track" else null
        AudioPlayback.playOneShot(processed, 1.0f, context, chokeGroup)
    }

    companion object {
        private const val ROOT_NOTE = 60
    }
}
