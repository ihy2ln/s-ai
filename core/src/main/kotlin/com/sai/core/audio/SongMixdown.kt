package com.sai.core.audio

import com.sai.core.tracker.Arrangement
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.PlaylistClip
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Offline stereo mixdown of a tracker song through Channel Rack + mixer routing. */
object SongMixdown {

    private const val ROOT_NOTE = 60

    fun render(
        song: Song,
        phrases: Map<Int, Phrase>,
        bpm: Int,
        samplesById: Map<Int, Wav>,
        channels: List<MixerMath.Channel>,
        strips: List<MixerMath.Strip>,
        mixerMaster: Float,
        masterMuted: Boolean,
        projectMaster: Float,
        pitchSemitones: Int,
        masterInsert: InsertSlot = InsertSlot(),
        chokeSameTrack: Boolean = false,
        sampleRate: Int = 44100,
        patternLengthAt: (Int) -> Int = { Phrase.DEFAULT_LENGTH },
        swingPercent: Int = 0,
        clips: List<PlaylistClip> = emptyList(),
        onlyTrack: Int? = null,
        audioOnly: Boolean = false,
    ): Wav {
        val stepFrames = (sampleRate * 60.0 / bpm.coerceAtLeast(1) / 4.0).toInt().coerceAtLeast(1)
        val totalSteps = Arrangement.totalSteps(clips, song, patternLengthAt)
        val hits = collectHits(
            song, phrases, stepFrames, totalSteps, patternLengthAt, swingPercent, clips, onlyTrack, audioOnly,
        )
        val anyRackSolo = MixerMath.anyRackSolo(channels)

        var extra = InsertFx.tailFrames(masterInsert, sampleRate)
        for (hit in hits) {
            val wav = samplesById[hit.step.instrument] ?: continue
            val rate = if (hit.pitched) rateFor(hit.step.note ?: ROOT_NOTE, pitchSemitones) else 1.0
            val channel = channels.getOrElse(hit.track) { MixerMath.Channel() }
            val insert = MixerMath.stripInsert(channel, strips)
            val stretched = (wav.frameCount * InsertFx.lengthFactor(insert) / rate).toInt() + 1
            extra = maxOf(extra, stretched + InsertFx.tailFrames(insert, sampleRate))
        }
        val totalFrames = mixdownFrameCount(totalSteps, stepFrames, swingPercent) + extra
        val left = DoubleArray(totalFrames)
        val right = DoubleArray(totalFrames)

        val byTrack = hits.groupBy { it.track }
        for ((track, trackHits) in byTrack) {
            val sorted = trackHits.sortedBy { it.startFrame }
            for (index in sorted.indices) {
                val hit = sorted[index]
                val nextHit = if (chokeSameTrack) sorted.getOrNull(index + 1)?.startFrame else null
                mixHit(
                    hit = hit,
                    chokeAt = nextHit,
                    stepFrames = stepFrames,
                    samplesById = samplesById,
                    channel = channels.getOrElse(track) { MixerMath.Channel() },
                    strips = strips,
                    mixerMaster = mixerMaster,
                    masterMuted = masterMuted,
                    projectMaster = projectMaster,
                    pitchSemitones = pitchSemitones,
                    anyRackSolo = anyRackSolo,
                    left = left,
                    right = right,
                )
            }
        }

        val samples = ShortArray(totalFrames * 2)
        for (i in 0 until totalFrames) {
            samples[i * 2] = (left[i] * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            samples[i * 2 + 1] = (right[i] * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return InsertFx.apply(Wav(sampleRate, 2, samples), masterInsert)
    }

    private data class Hit(
        val startFrame: Int,
        val track: Int,
        val step: Step,
        val pitched: Boolean = true,
    )

    private fun collectHits(
        song: Song,
        phrases: Map<Int, Phrase>,
        stepFrames: Int,
        totalSteps: Int,
        patternLengthAt: (Int) -> Int,
        swingPercent: Int,
        clips: List<PlaylistClip>,
        onlyTrack: Int?,
        audioOnly: Boolean,
    ): List<Hit> {
        val hits = mutableListOf<Hit>()
        var frame = 0
        repeat(totalSteps) { globalStep ->
            if (!audioOnly) {
                for (event in Arrangement.patternEventsAt(clips, song, phrases, globalStep, patternLengthAt)) {
                    if (onlyTrack != null && event.track != onlyTrack) continue
                    hits.add(Hit(frame, event.track, event.step, pitched = true))
                }
            }
            if (onlyTrack == null) {
                for (clip in Arrangement.audioStartingAt(clips, globalStep)) {
                    hits.add(
                        Hit(
                            startFrame = frame,
                            track = clip.lane.coerceIn(0, Arrangement.LANES - 1),
                            step = Step(
                                note = ROOT_NOTE,
                                instrument = clip.sampleId,
                                volume = 127,
                                length = clip.length,
                            ),
                            pitched = false,
                        ),
                    )
                }
            }
            frame += (Swing.intervalFraction(globalStep, swingPercent) * stepFrames).toInt().coerceAtLeast(1)
        }
        return hits
    }

    private fun mixdownFrameCount(
        totalSteps: Int,
        stepFrames: Int,
        swingPercent: Int,
    ): Int {
        var frames = 0
        for (step in 0 until totalSteps) {
            frames += (Swing.intervalFraction(step, swingPercent) * stepFrames).toInt().coerceAtLeast(1)
        }
        return frames
    }

    private fun mixHit(
        hit: Hit,
        chokeAt: Int?,
        stepFrames: Int,
        samplesById: Map<Int, Wav>,
        channel: MixerMath.Channel,
        strips: List<MixerMath.Strip>,
        mixerMaster: Float,
        masterMuted: Boolean,
        projectMaster: Float,
        pitchSemitones: Int,
        anyRackSolo: Boolean,
        left: DoubleArray,
        right: DoubleArray,
    ) {
        if (!MixerMath.isAudible(channel, strips, masterMuted, anyRackSolo)) return
        val id = hit.step.instrument ?: return
        val source = samplesById[id] ?: return
        val linear = MixerMath.linearGain(
            stepVolume = hit.step.volume ?: 127,
            rackVolume = channel.volume,
            stripVolume = MixerMath.stripVolume(channel, strips),
            mixerMaster = mixerMaster,
            projectMaster = projectMaster,
        )
        if (linear <= 0f) return

        val rate = if (hit.pitched) rateFor(hit.step.note ?: ROOT_NOTE, pitchSemitones) else 1.0
        var voice = if (rate == 1.0) source else SampleWarp.resample(source, rate)
        val gateFrames = hit.step.length?.takeIf { it > 0 }?.let { (it * stepFrames).coerceAtLeast(1) }
        if (gateFrames != null) {
            voice = Envelope.gate(voice, gateFrames)
        }
        voice = InsertFx.apply(voice, MixerMath.stripInsert(channel, strips))
        val pan = RackMix.shaperPan(channel.pan)
        val angle = (pan + 1.0) / 2.0 * (PI / 2.0)
        val leftGain = cos(angle) * linear
        val rightGain = sin(angle) * linear

        val frames = voice.frameCount
        val end = (chokeAt ?: (hit.startFrame + frames)).coerceAtMost(left.size)
        val channels = voice.channels
        var frame = 0
        var dest = hit.startFrame
        while (frame < frames && dest < end) {
            val l: Double
            val r: Double
            if (channels == 1) {
                val s = voice.samples[frame] / 32768.0
                l = s
                r = s
            } else {
                l = voice.samples[frame * channels] / 32768.0
                r = voice.samples[frame * channels + 1] / 32768.0
            }
            left[dest] += l * leftGain
            right[dest] += r * rightGain
            frame++
            dest++
        }
    }

    private fun rateFor(note: Int, pitchSemitones: Int): Double =
        2.0.pow((note + pitchSemitones - ROOT_NOTE) / 12.0)
}
