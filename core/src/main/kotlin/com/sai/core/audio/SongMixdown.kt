package com.sai.core.audio

import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import com.sai.core.tracker.TrackerEngine
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
        chokeSameTrack: Boolean = false,
        sampleRate: Int = 44100,
        patternLengthAt: (Int) -> Int = { Phrase.DEFAULT_LENGTH },
        swingPercent: Int = 0,
    ): Wav {
        val stepFrames = (sampleRate * 60.0 / bpm.coerceAtLeast(1) / 4.0).toInt().coerceAtLeast(1)
        val totalSteps = song.positions.indices.sumOf { Phrase.coerceLength(patternLengthAt(it)) }
        val hits = collectHits(song, phrases, stepFrames, totalSteps, patternLengthAt, swingPercent)
        val anyRackSolo = MixerMath.anyRackSolo(channels)

        var extra = 0
        for (hit in hits) {
            val wav = samplesById[hit.step.instrument] ?: continue
            val rate = rateFor(hit.step.note ?: ROOT_NOTE, pitchSemitones)
            extra = maxOf(extra, (wav.frameCount / rate).toInt() + 1)
        }
        val totalFrames = mixdownFrameCount(song, patternLengthAt, stepFrames, swingPercent) + extra
        val left = DoubleArray(totalFrames)
        val right = DoubleArray(totalFrames)

        val byTrack = hits.groupBy { it.track }
        for ((track, trackHits) in byTrack) {
            val sorted = trackHits.sortedBy { it.startFrame }
            for (index in sorted.indices) {
                val hit = sorted[index]
                val nextHit = if (chokeSameTrack) sorted.getOrNull(index + 1)?.startFrame else null
                val gateAt = hit.step.length?.takeIf { it > 0 }?.let { hit.startFrame + it * stepFrames }
                val cutAt = listOfNotNull(nextHit, gateAt).minOrNull()
                mixHit(
                    hit = hit,
                    cutAt = cutAt,
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
        return Wav(sampleRate, 2, samples)
    }

    private data class Hit(val startFrame: Int, val track: Int, val step: Step)

    private fun collectHits(
        song: Song,
        phrases: Map<Int, Phrase>,
        stepFrames: Int,
        totalSteps: Int,
        patternLengthAt: (Int) -> Int,
        swingPercent: Int,
    ): List<Hit> {
        val engine = TrackerEngine(song, phrases, patternLengthAt)
        val hits = mutableListOf<Hit>()
        var frame = 0
        repeat(totalSteps) {
            val step = engine.stepIndex
            val events = engine.advance()
            for (event in events) {
                hits.add(Hit(frame, event.track, event.step))
            }
            frame += (Swing.intervalFraction(step, swingPercent) * stepFrames).toInt().coerceAtLeast(1)
        }
        return hits
    }

    private fun mixdownFrameCount(
        song: Song,
        patternLengthAt: (Int) -> Int,
        stepFrames: Int,
        swingPercent: Int,
    ): Int {
        var frames = 0
        for (position in song.positions.indices) {
            val length = Phrase.coerceLength(patternLengthAt(position))
            for (step in 0 until length) {
                frames += (Swing.intervalFraction(step, swingPercent) * stepFrames).toInt().coerceAtLeast(1)
            }
        }
        return frames
    }

    private fun mixHit(
        hit: Hit,
        cutAt: Int?,
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

        val rate = rateFor(hit.step.note ?: ROOT_NOTE, pitchSemitones)
        val pitched = if (rate == 1.0) source else SampleWarp.resample(source, rate)
        val pan = RackMix.shaperPan(channel.pan)
        val angle = (pan + 1.0) / 2.0 * (PI / 2.0)
        val leftGain = cos(angle) * linear
        val rightGain = sin(angle) * linear

        val frames = pitched.frameCount
        val end = (cutAt ?: (hit.startFrame + frames)).coerceAtMost(left.size)
        val channels = pitched.channels
        var frame = 0
        var dest = hit.startFrame
        while (frame < frames && dest < end) {
            val l: Double
            val r: Double
            if (channels == 1) {
                val s = pitched.samples[frame] / 32768.0
                l = s
                r = s
            } else {
                l = pitched.samples[frame * channels] / 32768.0
                r = pitched.samples[frame * channels + 1] / 32768.0
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
