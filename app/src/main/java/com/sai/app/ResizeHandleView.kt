package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Visual divider bar between modules. Drag handling lives on [ModuleStackView]. */
class ResizeHandleView(context: Context) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppTheme.border
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppTheme.textSecondary
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setBackgroundColor(AppTheme.surfaceMuted)
        contentDescription = "Drag to resize modules"
        isClickable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height / 2f
        val cx = width / 2f
        canvas.drawLine(16f, midY, width - 16f, midY, trackPaint)
        canvas.drawLine(cx - 52f, midY, cx + 52f, midY, gripPaint)
        canvas.drawLine(cx - 32f, midY - 8f, cx + 32f, midY - 8f, gripPaint)
        canvas.drawLine(cx - 32f, midY + 8f, cx + 32f, midY + 8f, gripPaint)
    }
}
