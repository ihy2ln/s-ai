package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** A draggable horizontal bar between two modules; dragging it up/down resizes the module above. */
class ResizeHandleView(context: Context) : View(context) {

    var onDrag: ((deltaPx: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private var lastY = 0f
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 125, 132)
        strokeWidth = 3f
    }

    init {
        setBackgroundColor(Color.rgb(45, 47, 52))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height / 2f
        val cx = width / 2f
        canvas.drawLine(cx - 28f, midY, cx + 28f, midY, gripPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.rawY
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - lastY
                lastY = event.rawY
                onDrag?.invoke(dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onDragEnd?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
