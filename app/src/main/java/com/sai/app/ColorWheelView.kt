package com.sai.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** A tappable HSV color wheel: angle = hue, radius = saturation, fixed full value. */
class ColorWheelView(context: Context) : View(context) {

    var onColorPicked: ((Int) -> Unit)? = null
    private var selected: Int = Color.WHITE

    private var wheelBitmap: Bitmap? = null
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private var markerX = 0f
    private var markerY = 0f
    private var haveMarker = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        wheelBitmap = buildWheel(min(min(w, h), MAX_BITMAP_SIZE))
    }

    private fun buildWheel(size: Int): Bitmap? {
        if (size <= 0) return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val radius = size / 2f
        val hsv = FloatArray(3)
        hsv[2] = 1f
        for (py in 0 until size) {
            for (px in 0 until size) {
                val dx = px - radius
                val dy = py - radius
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist <= radius) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    hsv[0] = angle
                    hsv[1] = (dist / radius).coerceIn(0f, 1f)
                    bmp.setPixel(px, py, Color.HSVToColor(hsv))
                } else {
                    bmp.setPixel(px, py, Color.TRANSPARENT)
                }
            }
        }
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = wheelBitmap ?: return
        val displaySize = min(width, height).toFloat()
        val left = (width - displaySize) / 2f
        val top = (height - displaySize) / 2f
        val dst = android.graphics.RectF(left, top, left + displaySize, top + displaySize)
        canvas.drawBitmap(bmp, null, dst, null)
        if (haveMarker) {
            canvas.drawCircle(markerX, markerY, 14f, markerPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                pickAt(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pickAt(x: Float, y: Float) {
        val bmp = wheelBitmap ?: return
        val displaySize = min(width, height).toFloat()
        val left = (width - displaySize) / 2f
        val top = (height - displaySize) / 2f
        val bx = ((x - left) / displaySize * bmp.width).toInt()
        val by = ((y - top) / displaySize * bmp.height).toInt()
        if (bx !in 0 until bmp.width || by !in 0 until bmp.height) return
        val color = bmp.getPixel(bx, by)
        if (color == Color.TRANSPARENT) return
        selected = color
        markerX = x
        markerY = y
        haveMarker = true
        invalidate()
        onColorPicked?.invoke(color)
    }

    fun currentColor(): Int = selected

    private companion object {
        const val MAX_BITMAP_SIZE = 220
    }
}
