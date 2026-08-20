package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Yellow "T" inside a yellow circle outline on a black background — the tap-tempo control icon. */
class TempoIconView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val yellow = TransportShapeButton.TAP_YELLOW

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellow
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = yellow
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val pad = width * 0.18f
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - pad
        canvas.drawCircle(cx, cy, radius, ringPaint)

        textPaint.textSize = radius * 1.05f
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("T", cx, textY, textPaint)
    }
}
