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

    /** Highlight the current playback step column (-1 = none). */
    var playheadStep: Int = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Return false to reject the change (e.g. no instrument assigned yet); the view will not visually flip. */
    var onStepToggleRequested: ((index: Int, desiredOn: Boolean) -> Boolean)? = null

    private var states = BooleanArray(stepCount)
    private var paintValue = false
    private var lastIndex = -1

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 110, 130) }
    private val offGroupAPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(38, 42, 50) }
    private val offGroupBPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(48, 32, 36) }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val playheadBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * context.resources.displayMetrics.density
    }
    private val gapPx = 2f * context.resources.displayMetrics.density
    private val cornerPx = 2f * context.resources.displayMetrics.density

    fun setStates(newStates: BooleanArray) {
        states = newStates.copyOf(stepCount)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stepCount <= 0 || width <= 0) return
        val cellWidth = width.toFloat() / stepCount
        val side = minOf(cellWidth, height.toFloat()) - gapPx
        if (side <= 0f) return
        val top = (height - side) / 2f
        val bottom = top + side

        for (i in 0 until stepCount) {
            val left = i * cellWidth + (cellWidth - side) / 2f
            val paint = when {
                states.getOrElse(i) { false } -> onPaint
                (i / 4) % 2 == 0 -> offGroupAPaint
                else -> offGroupBPaint
            }
            canvas.drawRoundRect(left, top, left + side, bottom, cornerPx, cornerPx, paint)
        }

        if (playheadStep in 0 until stepCount) {
            val left = playheadStep * cellWidth + (cellWidth - side) / 2f
            canvas.drawRoundRect(left, top, left + side, bottom, cornerPx, cornerPx, playheadPaint)
            canvas.drawRoundRect(left, top, left + side, bottom, cornerPx, cornerPx, playheadBorderPaint)
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
                TouchScrollGuard.lock(this)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (index != lastIndex) applyIndex(index)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                TouchScrollGuard.unlock(this)
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
