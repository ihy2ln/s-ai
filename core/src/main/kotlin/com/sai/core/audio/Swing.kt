package com.sai.core.audio

/** Delays odd 16th notes. 0 is straight; 100 pushes offbeats halfway toward the next even step. */
object Swing {

    fun oddDelayFraction(swingPercent: Int): Double =
        swingPercent.coerceIn(0, 100) / 100.0 * 0.5

    /** Length of the interval *after* [stepIndex], as a fraction of a straight 16th. */
    fun intervalFraction(stepIndex: Int, swingPercent: Int): Double {
        val delay = oddDelayFraction(swingPercent)
        return if (stepIndex % 2 == 0) 1.0 + delay else 1.0 - delay
    }

    fun startFrame(stepIndex: Int, stepFrames: Int, swingPercent: Int): Int {
        if (stepIndex <= 0) return 0
        if (swingPercent <= 0) return stepIndex * stepFrames
        var acc = 0.0
        for (i in 0 until stepIndex) {
            acc += intervalFraction(i, swingPercent) * stepFrames
        }
        return acc.toInt()
    }
}
