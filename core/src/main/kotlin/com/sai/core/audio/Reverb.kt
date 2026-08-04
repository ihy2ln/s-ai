package com.sai.core.audio

/** A small Schroeder-style reverb: parallel comb filters feeding series allpass filters. */
object Reverb {

    private val combTunings = intArrayOf(1116, 1188, 1277, 1356)
    private val allpassTunings = intArrayOf(556, 441)

    /**
     * @param size room size / decay length, 0..1
     * @param damp high-frequency damping in the feedback path, 0..1
     * @param mix dry/wet balance, 0 = fully dry, 1 = fully wet
     */
    fun apply(wav: Wav, size: Double, damp: Double, mix: Double): Wav {
        val feedback = 0.7 + size.coerceIn(0.0, 1.0) * 0.28
        val damping = damp.coerceIn(0.0, 1.0)
        val wetMix = mix.coerceIn(0.0, 1.0)
        val scale = wav.sampleRate / 44100.0

        val out = ShortArray(wav.samples.size)
        for (channel in 0 until wav.channels) {
            val combs = combTunings.map { CombFilter((it * scale).toInt().coerceAtLeast(1), feedback, damping) }
            val allpasses = allpassTunings.map { AllpassFilter((it * scale).toInt().coerceAtLeast(1), 0.5) }

            var frame = channel
            while (frame < wav.samples.size) {
                val dry = wav.samples[frame] / 32768.0
                var wet = 0.0
                for (comb in combs) wet += comb.process(dry)
                wet /= combs.size
                for (allpass in allpasses) wet = allpass.process(wet)

                val output = dry * (1 - wetMix) + wet * wetMix
                out[frame] = (output * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                frame += wav.channels
            }
        }
        return wav.copy(samples = out)
    }

    private class CombFilter(size: Int, private val feedback: Double, private val damp: Double) {
        private val buffer = DoubleArray(size)
        private var index = 0
        private var stored = 0.0

        fun process(input: Double): Double {
            val output = buffer[index]
            stored = output * (1 - damp) + stored * damp
            buffer[index] = input + stored * feedback
            index = (index + 1) % buffer.size
            return output
        }
    }

    private class AllpassFilter(size: Int, private val feedback: Double) {
        private val buffer = DoubleArray(size)
        private var index = 0

        fun process(input: Double): Double {
            val bufOut = buffer[index]
            val output = -input + bufOut
            buffer[index] = input + bufOut * feedback
            index = (index + 1) % buffer.size
            return output
        }
    }
}
