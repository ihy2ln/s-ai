package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Draws a simple geometric icon for transport controls. */
class ShapeIconView(context: Context) : View(context) {

    enum class Shape { TRIANGLE, SQUARE, CIRCLE, DIAMOND, PAUSE }

    var shape: Shape = Shape.TRIANGLE
        set(value) {
            field = value
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            fillPaint.color = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = iconColor
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = width * 0.22f
        val left = pad
        val top = pad
        val right = width - pad
        val bottom = height - pad
        when (shape) {
            Shape.SQUARE -> canvas.drawRect(left, top, right, bottom, fillPaint)
            Shape.CIRCLE -> {
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, minOf(right - left, bottom - top) / 2f, fillPaint)
            }
            Shape.DIAMOND -> {
                val path = Path().apply {
                    moveTo(width / 2f, top)
                    lineTo(right, height / 2f)
                    lineTo(width / 2f, bottom)
                    lineTo(left, height / 2f)
                    close()
                }
                canvas.drawPath(path, fillPaint)
            }
            Shape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(left + (right - left) * 0.15f, top)
                    lineTo(right, height / 2f)
                    lineTo(left + (right - left) * 0.15f, bottom)
                    close()
                }
                canvas.drawPath(path, fillPaint)
            }
            Shape.PAUSE -> {
                val barWidth = (right - left) * 0.28f
                val gap = (right - left) * 0.16f
                val cx = width / 2f
                canvas.drawRoundRect(
                    RectF(cx - gap / 2f - barWidth, top, cx - gap / 2f, bottom),
                    barWidth * 0.2f,
                    barWidth * 0.2f,
                    fillPaint,
                )
                canvas.drawRoundRect(
                    RectF(cx + gap / 2f, top, cx + gap / 2f + barWidth, bottom),
                    barWidth * 0.2f,
                    barWidth * 0.2f,
                    fillPaint,
                )
            }
        }
    }
}
