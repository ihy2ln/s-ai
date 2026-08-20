package com.sai.app

import android.content.Context
import kotlin.math.log10
import kotlin.math.pow

/** Applies global project pitch and master volume to playback. */
object ProjectPlayback {

    fun pitchSemitones(context: Context): Int = TrackerProjectStore.get(context).pitchSemitones

    fun masterVolume(context: Context): Int = TrackerProjectStore.get(context).masterVolume

    /** Playback rate for a step note, including the project pitch offset. */
    fun rateForNote(context: Context, note: Int, rootNote: Int = 60): Float {
        val adjusted = note + pitchSemitones(context)
        return 2.0.pow((adjusted - rootNote) / 12.0).toFloat()
    }

    /** Combines step volume with project master volume and returns gain in dB. */
    fun gainDb(context: Context, stepVolume: Int = 127): Double {
        val scaled = scaledVolume(context, stepVolume)
        return if (scaled <= 0) -80.0 else 20.0 * log10(scaled / 127.0)
    }

    fun scaledVolume(context: Context, stepVolume: Int = 127): Int {
        val master = masterVolume(context)
        return (stepVolume * master / 127.0).toInt().coerceIn(0, 127)
    }
}
