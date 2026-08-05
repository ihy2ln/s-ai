package com.sai.core.audio

import kotlin.math.abs
import kotlin.math.pow

object SampleEditor {

    fun trim(wav: Wav, startFrame: Int, endFrame: Int): Wav {
        require(startFrame in 0..wav.frameCount) { "startFrame out of range" }
        require(endFrame in startFrame..wav.frameCount) { "endFrame out of range" }
        val from = startFrame * wav.channels
        val to = endFrame * wav.channels
        return wav.copy(samples = wav.samples.copyOfRange(from, to))
    }

    fun gain(wav: Wav, gainDb: Double): Wav {
        val factor = 10.0.pow(gainDb / 20.0)
        return wav.copy(samples = scale(wav.samples, factor))
    }

    fun reverse(wav: Wav): Wav {
        val out = ShortArray(wav.samples.size)
        val frames = wav.frameCount
        val channels = wav.channels
        for (frame in 0 until frames) {
            val srcBase = frame * channels
            val dstBase = (frames - 1 - frame) * channels
            for (c in 0 until channels) out[dstBase + c] = wav.samples[srcBase + c]
        }
        return wav.copy(samples = out)
    }

    fun normalize(wav: Wav, targetPeak: Double = Short.MAX_VALUE.toDouble()): Wav {
        val peak = wav.samples.maxOfOrNull { abs(it.toInt()) } ?: 0
        if (peak == 0) return wav
        return wav.copy(samples = scale(wav.samples, targetPeak / peak))
    }

    /** Removes [startFrame] until [endFrame], returning the remaining audio joined back together
     *  (the cut portion itself isn't kept here - the caller is expected to [trim] it out first
     *  if it wants to hold onto it, e.g. for a clipboard). */
    fun cut(wav: Wav, startFrame: Int, endFrame: Int): Wav {
        val before = trim(wav, 0, startFrame)
        val after = trim(wav, endFrame, wav.frameCount)
        return concat(before, after)
    }

    /** Inserts [clip] into [wav] at [atFrame]. [clip]'s channel count is coerced to match [wav]'s;
     *  its sample rate must already match (thrown as an [IllegalArgumentException] otherwise). */
    fun insert(wav: Wav, atFrame: Int, clip: Wav): Wav {
        val at = atFrame.coerceIn(0, wav.frameCount)
        val before = trim(wav, 0, at)
        val after = trim(wav, at, wav.frameCount)
        return concat(concat(before, clip), after)
    }

    /** Joins [a] and [b] end to end. Both must share a sample rate; [b]'s channel count is
     *  coerced to match [a]'s so mono/stereo clips can still be joined together. */
    fun concat(a: Wav, b: Wav): Wav {
        require(a.sampleRate == b.sampleRate) { "Sample rates must match to join audio (${a.sampleRate}Hz vs ${b.sampleRate}Hz)" }
        val matched = matchChannels(b, a.channels)
        return a.copy(samples = a.samples + matched.samples)
    }

    /** Downmixes (average) or upmixes (duplicate) [wav] to [targetChannels]. */
    fun matchChannels(wav: Wav, targetChannels: Int): Wav {
        if (wav.channels == targetChannels) return wav
        val frames = wav.frameCount
        return if (targetChannels == 1) {
            val out = ShortArray(frames)
            for (f in 0 until frames) {
                var sum = 0
                for (c in 0 until wav.channels) sum += wav.samples[f * wav.channels + c]
                out[f] = (sum / wav.channels).toShort()
            }
            wav.copy(channels = 1, samples = out)
        } else {
            val out = ShortArray(frames * targetChannels)
            for (f in 0 until frames) {
                val v = wav.samples[f * wav.channels]
                for (c in 0 until targetChannels) out[f * targetChannels + c] = v
            }
            wav.copy(channels = targetChannels, samples = out)
        }
    }

    private fun scale(samples: ShortArray, factor: Double): ShortArray =
        ShortArray(samples.size) { i ->
            (samples[i] * factor).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
}
