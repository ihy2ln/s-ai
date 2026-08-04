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

    private fun scale(samples: ShortArray, factor: Double): ShortArray =
        ShortArray(samples.size) { i ->
            (samples[i] * factor).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
}
