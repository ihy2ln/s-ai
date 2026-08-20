package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** FL Studio-style track mute LED (green = audible, dim = muted). */
class MuteLedView(context: Context) : View(context) {

    var muted: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onToggle: (() -> Unit)? = null

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 200, 80) }
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(50, 55, 60) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(80, 85, 95)
        strokeWidth = 1.5f * context.resources.displayMetrics.density
    }

    init {
        isClickable = true
        setOnClickListener { onToggle?.invoke() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ringPaint.strokeWidth
        canvas.drawCircle(cx, cy, radius, if (muted) offPaint else onPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }
}
