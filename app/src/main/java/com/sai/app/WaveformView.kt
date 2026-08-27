package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = AppTheme.accentColor(context)
        strokeWidth = 1.4f
    }
    private val markerPaint = Paint().apply {
        color = AppTheme.withAlpha(AppTheme.textPrimary, 180)
        strokeWidth = 2f
    }
    private val backPaint = Paint().apply { color = AppTheme.surfaceMuted }

    var channels: Int = 1
    var samples: ShortArray = ShortArray(0)
        set(value) {
            field = value
            invalidate()
        }
    var sliceBoundaries: List<Int> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backPaint)
        val frameCount = if (channels > 0) samples.size / channels else 0
        if (frameCount == 0 || width == 0) return

        val midY = height / 2f
        val framesPerPixel = max(1, frameCount / width)

        var x = 0f
        var frame = 0
        while (frame < frameCount && x < width) {
            var min = 0
            var max = 0
            val end = (frame + framesPerPixel).coerceAtMost(frameCount)
            for (f in frame until end) {
                val sample = samples[f * channels].toInt()
                if (sample < min) min = sample
                if (sample > max) max = sample
            }
            val minY = midY - (min / 32768f) * midY
            val maxY = midY - (max / 32768f) * midY
            canvas.drawLine(x, minY, x, maxY, paint)
            x += 1f
            frame += framesPerPixel
        }

        for (boundary in sliceBoundaries) {
            val markerX = (boundary.toFloat() / frameCount) * width
            canvas.drawLine(markerX, 0f, markerX, height.toFloat(), markerPaint)
        }
    }
}
