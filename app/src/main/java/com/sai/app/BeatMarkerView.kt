package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** FL Studio-style beat-group markers above the step grid (groups of four steps). */
class BeatMarkerView(context: Context) : View(context) {

    var stepCount = 16
        set(value) {
            field = value
            invalidate()
        }

    private val groupAPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 170, 185) }
    private val groupBPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(185, 55, 55) }
    private val gapPx = 2f * context.resources.displayMetrics.density
    private val cornerPx = 2f * context.resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stepCount <= 0 || width <= 0) return
        val cellWidth = width.toFloat() / stepCount
        for (i in 0 until stepCount) {
            val left = i * cellWidth + gapPx / 2
            val right = (i + 1) * cellWidth - gapPx / 2
            val paint = if ((i / 4) % 2 == 0) groupAPaint else groupBPaint
            canvas.drawRoundRect(left, 0f, right, height.toFloat(), cornerPx, cornerPx, paint)
        }
    }
}
