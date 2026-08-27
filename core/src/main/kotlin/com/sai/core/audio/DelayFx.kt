package com.sai.core.audio

/** Simple feedback delay line. Time in milliseconds, feedback and mix 0..1. */
object DelayFx {

    fun apply(wav: Wav, timeMs: Double, feedback: Double, mix: Double): Wav {
        val delayFrames = ((timeMs.coerceIn(1.0, 2000.0) / 1000.0) * wav.sampleRate).toInt().coerceAtLeast(1)
        val fb = feedback.coerceIn(0.0, 0.95)
        val wetMix = mix.coerceIn(0.0, 1.0)
        val tail = (delayFrames * (1 + fb * 8)).toInt().coerceAtMost(wav.sampleRate * 3)
        val outFrames = wav.frameCount + tail
        val out = ShortArray(outFrames * wav.channels)
        val line = DoubleArray(delayFrames * wav.channels)

        for (channel in 0 until wav.channels) {
            var write = 0
            for (frame in 0 until outFrames) {
                val input = if (frame < wav.frameCount) {
                    wav.samples[frame * wav.channels + channel] / 32768.0
                } else {
                    0.0
                }
                val delayed = line[write * wav.channels + channel]
                val wet = input + delayed * fb
                line[write * wav.channels + channel] = wet
                write = (write + 1) % delayFrames
                val mixed = input * (1.0 - wetMix) + delayed * wetMix
                out[frame * wav.channels + channel] =
                    (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return wav.copy(samples = out)
    }

    fun tailFrames(timeMs: Double, feedback: Double, mix: Double, sampleRate: Int): Int {
        if (mix <= 0.0) return 0
        val delayFrames = ((timeMs.coerceIn(1.0, 2000.0) / 1000.0) * sampleRate).toInt().coerceAtLeast(1)
        val fb = feedback.coerceIn(0.0, 0.95)
        return (delayFrames * (1 + fb * 8) * mix).toInt().coerceAtLeast(0)
    }
}
