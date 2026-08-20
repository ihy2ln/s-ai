package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.sin

/** Basic synthesizer waveforms for procedural sound generation. */
enum class Waveform {
    SINE, SAW, TRIANGLE, SQUARE,
}

object Oscillator {
    const val DEFAULT_SAMPLE_RATE = 44100
    const val DEFAULT_DURATION_SEC = 1.0

    fun displayName(waveform: Waveform): String = when (waveform) {
        Waveform.SINE -> "Circle"
        Waveform.SAW -> "Saw"
        Waveform.TRIANGLE -> "Triangle"
        Waveform.SQUARE -> "Square"
    }

    fun generate(
        waveform: Waveform,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channels: Int = 1,
        durationSec: Double = DEFAULT_DURATION_SEC,
        freqHz: Double = 440.0,
        amplitude: Double = 0.5,
    ): Wav {
        val frames = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val samples = ShortArray(frames * channels)
        for (frame in 0 until frames) {
            val phase = (freqHz * frame / sampleRate) % 1.0
            val normalized = when (waveform) {
                Waveform.SINE -> sin(2.0 * PI * phase)
                Waveform.SAW -> 2.0 * phase - 1.0
                Waveform.TRIANGLE -> {
                    val t = phase
                    if (t < 0.5) 4.0 * t - 1.0 else 3.0 - 4.0 * t
                }
                Waveform.SQUARE -> if (phase < 0.5) 1.0 else -1.0
            }
            val value = (amplitude * Short.MAX_VALUE * normalized)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            for (channel in 0 until channels) {
                samples[frame * channels + channel] = value
            }
        }
        return Wav(sampleRate, channels, samples)
    }
}
