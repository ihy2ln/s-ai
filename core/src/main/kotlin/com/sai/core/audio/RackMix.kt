package com.sai.core.audio

/** Combines Channel Rack mute / volume / pan with a tracker step for playback. */
object RackMix {

    fun shouldPlay(muted: Boolean): Boolean = !muted

    /** Step volume is 0–127; rack volume is 0–1 linear. */
    fun combinedStepVolume(stepVolume: Int, rackVolume: Float): Int {
        val step = stepVolume.coerceIn(0, 127)
        val rack = rackVolume.coerceIn(0f, 1f)
        return (step * rack).toInt().coerceIn(0, 127)
    }

    /** Rack pan 0..1 (0.5 = center) to [StereoShaper] pan −1..1. */
    fun shaperPan(rackPan: Float): Double = (rackPan.coerceIn(0f, 1f) * 2.0 - 1.0)
}
