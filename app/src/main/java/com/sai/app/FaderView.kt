package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Vertical mixer fader: 0 at the bottom, 1 at the top. Drag to change. */
class FaderView(context: Context) : View(context) {

    var value: Float = 1f
        private set

    var onChange: ((Float) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppTheme.surfaceRaised
        strokeWidth = 4f * context.resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppTheme.textPrimary }
    private val capAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppTheme.accentColor(context) }
    private val capRect = RectF()

    fun setValue(newValue: Float, notify: Boolean = false) {
        value = newValue.coerceIn(0f, 1f)
        invalidate()
        if (notify) onChange?.invoke(value)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val inset = 10f * resources.displayMetrics.density
        val top = inset
        val bottom = height - inset
        canvas.drawLine(cx, top, cx, bottom, trackPaint)

        val y = bottom - value * (bottom - top)
        val capW = 14f * resources.displayMetrics.density
        val capH = 8f * resources.displayMetrics.density
        capRect.set(cx - capW, y - capH, cx + capW, y + capH)
        canvas.drawRoundRect(capRect, 3f, 3f, capPaint)
        canvas.drawRect(cx - capW, y - 1.5f, cx + capW, y + 1.5f, capAccent)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                TouchScrollGuard.lock(this)
                applyY(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                applyY(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                TouchScrollGuard.unlock(this)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyY(y: Float) {
        val inset = 10f * resources.displayMetrics.density
        val top = inset
        val bottom = height - inset
        val next = ((bottom - y) / (bottom - top)).coerceIn(0f, 1f)
        if (next != value) {
            value = next
            invalidate()
            onChange?.invoke(value)
        }
    }
}
