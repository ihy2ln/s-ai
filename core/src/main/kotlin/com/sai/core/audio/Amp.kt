package com.sai.core.audio

/** Amp: drive into a dark tone control. Conservative mix default lives in InsertFx. */
object Amp {

    fun apply(wav: Wav, drive: Double, tone: Double, mix: Double): Wav {
        val crunch = Distortion.apply(wav, drive.coerceIn(0.0, 1.0), tone.coerceIn(0.0, 1.0), 1.0)
        val cutoff = 1800.0 + tone.coerceIn(0.0, 1.0) * 10000.0
        val shaped = Filter.apply(crunch, 80.0, cutoff, cutoff, 0.15, drive * 0.25, 0.0)
        val wetMix = mix.coerceIn(0.0, 1.0)
        if (wetMix >= 0.999) return shaped
        val out = ShortArray(wav.samples.size)
        val frames = minOf(wav.frameCount, shaped.frameCount)
        val channels = wav.channels
        for (frame in 0 until frames) {
            for (c in 0 until channels) {
                val i = frame * channels + c
                val dry = wav.samples[i] / 32768.0
                val wet = shaped.samples.getOrElse(i) { 0 } / 32768.0
                val mixed = dry * (1.0 - wetMix) + wet * wetMix
                out[i] = (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return wav.copy(samples = out)
    }
}
