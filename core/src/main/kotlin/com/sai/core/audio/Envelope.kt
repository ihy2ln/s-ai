package com.sai.core.audio

import kotlin.math.roundToInt

/** Amplitude envelope and note gating for one-shots. */
object Envelope {

    fun apply(
        wav: Wav,
        attackSec: Double,
        decaySec: Double,
        sustain: Double,
        releaseSec: Double,
    ): Wav {
        val sustainLevel = sustain.coerceIn(0.0, 1.0)
        val attack = secondsToFrames(wav, attackSec)
        val decay = secondsToFrames(wav, decaySec)
        val release = secondsToFrames(wav, releaseSec)
        if (attack == 0 && decay == 0 && release == 0 && sustainLevel >= 0.999) return wav

        val frames = wav.frameCount
        val out = ShortArray(wav.samples.size)
        val channels = wav.channels
        val decayEnd = (attack + decay).coerceAtMost(frames)
        val releaseStart = (frames - release).coerceAtLeast(decayEnd)

        for (frame in 0 until frames) {
            val gain = when {
                attack > 0 && frame < attack -> frame.toDouble() / attack
                frame < decayEnd && decay > 0 -> {
                    val t = (frame - attack).toDouble() / decay
                    1.0 + (sustainLevel - 1.0) * t
                }
                frame >= releaseStart && release > 0 -> {
                    val t = (frame - releaseStart).toDouble() / release
                    sustainLevel * (1.0 - t)
                }
                else -> sustainLevel
            }
            for (c in 0 until channels) {
                val index = frame * channels + c
                out[index] = (wav.samples[index] * gain).roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return wav.copy(samples = out)
    }

    /** Cuts [wav] after [frames] with a short linear fade so gated notes do not click. */
    fun gate(wav: Wav, frames: Int, fadeMs: Double = 8.0): Wav {
        val end = frames.coerceIn(1, wav.frameCount)
        val fade = secondsToFrames(wav, fadeMs / 1000.0).coerceAtMost(end)
        val trimmed = SampleEditor.trim(wav, 0, end)
        if (fade <= 0) return trimmed
        val out = trimmed.samples.copyOf()
        val channels = trimmed.channels
        val fadeStart = end - fade
        for (frame in fadeStart until end) {
            val gain = (end - frame).toDouble() / fade
            for (c in 0 until channels) {
                val index = frame * channels + c
                out[index] = (out[index] * gain).roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return trimmed.copy(samples = out)
    }

    private fun secondsToFrames(wav: Wav, seconds: Double): Int =
        (wav.sampleRate * seconds.coerceAtLeast(0.0)).toInt().coerceAtLeast(0)
}
