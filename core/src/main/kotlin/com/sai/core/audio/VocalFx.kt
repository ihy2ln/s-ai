package com.sai.core.audio

/** Light vocal treatment for recorded takes: high-pass rumble plus a gentle compressor. */
object VocalFx {

    fun apply(wav: Wav): Wav {
        val cleaned = Equalizer.apply(
            wav,
            bandGainsDb = DoubleArray(Equalizer.BAND_FREQS_HZ.size) { 0.0 },
            lowCutHz = 120.0,
            midCutHz = 0.0,
            highCutHz = 20000.0,
        )
        return Compressor.apply(
            cleaned,
            thresholdDb = -18.0,
            ratio = 3.0,
            attackMs = 8.0,
            releaseMs = 80.0,
            makeupGainDb = 4.0,
        )
    }
}
