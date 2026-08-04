package com.sai.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/** Animates a view up and down once per beat, so it visually reads as the current tempo. */
class TempoBouncer(private val view: View, private val bounceHeightPx: Float) {
    private var animator: ObjectAnimator? = null

    fun setBpm(bpm: Int) {
        animator?.cancel()
        view.translationY = 0f
        val beatMs = (60_000f / bpm.coerceIn(20, 300)).toLong().coerceAtLeast(50)
        animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -bounceHeightPx, 0f).apply {
            duration = beatMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        view.translationY = 0f
    }
}
