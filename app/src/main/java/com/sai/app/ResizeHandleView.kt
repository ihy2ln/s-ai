package com.sai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent

/** Full-width divider line between modules. Press and drag vertically to resize. */
class ResizeHandleView(context: Context) : View(context) {

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((deltaPx: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private var lastY = 0f
    private var dragging = false

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 74, 82)
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(168, 174, 184)
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 210, 220)
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setBackgroundColor(Color.rgb(32, 34, 38))
        isClickable = true
        isFocusable = true
        contentDescription = "Drag to resize modules"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height / 2f
        val cx = width / 2f
        val paint = if (dragging) activePaint else gripPaint
        canvas.drawLine(24f, midY, width - 24f, midY, trackPaint)
        canvas.drawLine(cx - 48f, midY, cx + 48f, midY, paint)
        canvas.drawLine(cx - 28f, midY - 7f, cx + 28f, midY - 7f, paint)
        canvas.drawLine(cx - 28f, midY + 7f, cx + 28f, midY + 7f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.rawY
                dragging = true
                invalidate()
                parentScroll()?.suppressIntercept = true
                disallowParentIntercept(true)
                onDragStart?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - lastY
                lastY = event.rawY
                if (kotlin.math.abs(dy) >= 0.5f || dragging) {
                    onDrag?.invoke(dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
                parentScroll()?.suppressIntercept = false
                disallowParentIntercept(false)
                onDragEnd?.invoke()
                return true
            }
        }
        return true
    }

    private fun parentScroll(): ModulesScrollView? {
        var parent: ViewParent? = parent
        while (parent != null) {
            if (parent is ModulesScrollView) return parent
            parent = parent.parent
        }
        return null
    }

    private fun disallowParentIntercept(disallow: Boolean) {
        var parent: ViewParent? = parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }
}
