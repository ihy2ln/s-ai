package com.sai.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/** Drives the beat indicator. While the sequencer is stopped, [startIdle] free-runs a preview
 *  pulse at the current BPM. While it's playing, the sequencer's own step callback should call
 *  [pulseOnce] on each downbeat instead - that ties the visible beat to the actual playback
 *  thread's timing rather than a UI animation running on its own independent clock. */
class TempoBouncer(private val view: View, private val bounceHeightPx: Float) {
    private var idleAnimator: ObjectAnimator? = null
    private var bpm = 120

    fun setBpm(newBpm: Int) {
        bpm = newBpm.coerceIn(20, 300)
    }

    fun startIdle() {
        idleAnimator?.cancel()
        view.translationY = 0f
        val beatMs = beatMs()
        idleAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -bounceHeightPx, 0f).apply {
            duration = beatMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /** A single pulse, meant to be triggered from the sequencer's step callback. */
    fun pulseOnce() {
        idleAnimator?.cancel()
        idleAnimator = null
        view.translationY = 0f
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -bounceHeightPx, 0f).apply {
            duration = beatMs().coerceAtMost(240)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun stop() {
        idleAnimator?.cancel()
        idleAnimator = null
        view.translationY = 0f
    }

    private fun beatMs(): Long = (60_000f / bpm).toLong().coerceAtLeast(50)
}
