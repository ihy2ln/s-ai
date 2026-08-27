package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Vertical peak meter, 0 at the bottom. */
class MeterView(context: Context) : View(context) {

    var level: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (field != next) {
                field = next
                invalidate()
            }
        }

    private val backPaint = Paint().apply { color = AppTheme.surfaceMuted }
    private val greenPaint = Paint().apply { color = AppTheme.play }
    private val yellowPaint = Paint().apply { color = AppTheme.gold }
    private val redPaint = Paint().apply { color = AppTheme.record }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, backPaint)
        if (level <= 0f) return
        val filled = h * level
        val top = h - filled
        val paint = when {
            level >= 0.9f -> redPaint
            level >= 0.7f -> yellowPaint
            else -> greenPaint
        }
        canvas.drawRect(0f, top, w, h, paint)
    }
}
