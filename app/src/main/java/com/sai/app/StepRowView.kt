package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * One step-sequencer row, drawn and hit-tested directly (not as N separate cell views) so a
 * single click-and-drag gesture can "paint" a run of steps on or off in one motion, FL-Studio style.
 */
class StepRowView(context: Context) : View(context) {

    var stepCount = 16
        set(value) {
            field = value
            states = BooleanArray(value)
            invalidate()
        }

    /** Return false to reject the change (e.g. no instrument assigned yet); the view will not visually flip. */
    var onStepToggleRequested: ((index: Int, desiredOn: Boolean) -> Boolean)? = null

    private var states = BooleanArray(stepCount)
    private var paintValue = false
    private var lastIndex = -1

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppTheme.accentColor(context) }
    private val offEvenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 42, 48) }
    private val offOddPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 32, 36) }
    private val gapPx = 3f * context.resources.displayMetrics.density

    fun setStates(newStates: BooleanArray) {
        states = newStates.copyOf(stepCount)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stepCount <= 0 || width <= 0) return
        val cellWidth = width.toFloat() / stepCount
        for (i in 0 until stepCount) {
            val left = i * cellWidth + gapPx / 2
            val right = (i + 1) * cellWidth - gapPx / 2
            val paint = when {
                states.getOrElse(i) { false } -> onPaint
                (i / 4) % 2 == 0 -> offEvenPaint
                else -> offOddPaint
            }
            canvas.drawRect(left, 0f, right, height.toFloat(), paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (stepCount <= 0 || width <= 0) return super.onTouchEvent(event)
        val cellWidth = width.toFloat() / stepCount
        val index = (event.x / cellWidth).toInt().coerceIn(0, stepCount - 1)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                paintValue = !states.getOrElse(index) { false }
                lastIndex = -1
                applyIndex(index)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (index != lastIndex) applyIndex(index)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyIndex(index: Int) {
        if (states.getOrElse(index) { false } != paintValue) {
            val allowed = onStepToggleRequested?.invoke(index, paintValue) ?: true
            if (allowed) {
                states[index] = paintValue
                invalidate()
            }
        }
        lastIndex = index
    }
}
