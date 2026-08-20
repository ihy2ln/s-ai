package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Draws a simple geometric icon for header menu buttons. */
class ShapeIconView(
    context: Context,
    private val shape: ShapeMenuButton.Shape,
) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
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
            ShapeMenuButton.Shape.SQUARE -> canvas.drawRect(left, top, right, bottom, fillPaint)
            ShapeMenuButton.Shape.CIRCLE -> {
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, minOf(right - left, bottom - top) / 2f, fillPaint)
            }
            ShapeMenuButton.Shape.DIAMOND -> {
                val path = Path().apply {
                    moveTo(width / 2f, top)
                    lineTo(right, height / 2f)
                    lineTo(width / 2f, bottom)
                    lineTo(left, height / 2f)
                    close()
                }
                canvas.drawPath(path, fillPaint)
            }
            ShapeMenuButton.Shape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(width / 2f, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                canvas.drawPath(path, fillPaint)
            }
        }
    }
}
