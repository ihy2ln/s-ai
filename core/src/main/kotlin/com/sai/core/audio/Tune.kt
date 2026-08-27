package com.sai.core.audio

/** Manual pitch-toward-note helper: mix a pitched copy with dry. Amount 0..1, note is MIDI. */
object Tune {

    fun apply(wav: Wav, amount: Double, note: Double): Wav {
        val mix = amount.coerceIn(0.0, 1.0)
        if (mix <= 0.0) return wav
        val midi = note.coerceIn(0.0, 127.0)
        val centsToward = (midi - 60.0).coerceIn(-12.0, 12.0) * mix
        val pitched = Filter.apply(wav, 80.0, 18000.0, 12000.0, 0.1, 0.0, centsToward)
        if (mix >= 0.999) return pitched
        val frames = minOf(wav.frameCount, pitched.frameCount)
        val channels = wav.channels
        val out = ShortArray(frames * channels)
        for (i in out.indices) {
            val dry = wav.samples.getOrElse(i) { 0 } / 32768.0
            val wet = pitched.samples.getOrElse(i) { 0 } / 32768.0
            val mixed = dry * (1.0 - mix) + wet * mix
            out[i] = (mixed * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return wav.copy(samples = out)
    }
}
