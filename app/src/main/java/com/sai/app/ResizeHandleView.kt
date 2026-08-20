package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent

/** A draggable horizontal bar between two modules; dragging it up/down resizes adjacent modules. */
class ResizeHandleView(context: Context) : View(context) {

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((deltaPx: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private var lastY = 0f
    private var dragging = false

    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 125, 132)
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val activeGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 175, 182)
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setBackgroundColor(Color.rgb(45, 47, 52))
        isClickable = true
        contentDescription = "Drag to resize modules"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height / 2f
        val cx = width / 2f
        val paint = if (dragging) activeGripPaint else gripPaint
        canvas.drawLine(cx - 36f, midY, cx + 36f, midY, paint)
        canvas.drawLine(cx - 22f, midY - 5f, cx + 22f, midY - 5f, paint)
        canvas.drawLine(cx - 22f, midY + 5f, cx + 22f, midY + 5f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.rawY
                dragging = true
                invalidate()
                disallowParentIntercept(true)
                onDragStart?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - lastY
                lastY = event.rawY
                if (dy != 0f) onDrag?.invoke(dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
                disallowParentIntercept(false)
                onDragEnd?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun disallowParentIntercept(disallow: Boolean) {
        var parent: ViewParent? = parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }
}
