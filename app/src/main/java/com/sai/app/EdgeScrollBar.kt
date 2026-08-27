package com.sai.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import kotlin.math.max

/** Thin far-edge scrollbar: drag this strip to scroll the module stack. */
class EdgeScrollBar(context: Context) : View(context) {

    var scrollTarget: ScrollView? = null
        set(value) {
            field?.setOnScrollChangeListener(null)
            field = value
            field?.setOnScrollChangeListener { _, _, _, _, _ -> invalidate() }
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppTheme.surfaceMuted
        style = Paint.Style.FILL
    }
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppTheme.surfaceRaised
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppTheme.accentColor(context)
        style = Paint.Style.FILL
    }
    private val thumbRect = RectF()
    private val railRect = RectF()

    init {
        contentDescription = "Scroll modules"
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), trackPaint)

        val inset = 4f * density
        railRect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(railRect, width / 2f, width / 2f, railPaint)

        val metrics = thumbMetrics() ?: return
        thumbRect.set(inset, metrics.first, width - inset, metrics.second)
        canvas.drawRoundRect(thumbRect, width / 2f, width / 2f, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scroll = scrollTarget ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scrollToTouch(scroll, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scrollToTouch(scroll: ScrollView, y: Float) {
        val range = scrollRange(scroll)
        if (range <= 0) return
        val inset = 4f * density
        val track = (height - inset * 2).coerceAtLeast(1f)
        val fraction = ((y - inset) / track).coerceIn(0f, 1f)
        scroll.scrollTo(0, (fraction * range).toInt())
        invalidate()
    }

    private fun thumbMetrics(): Pair<Float, Float>? {
        val scroll = scrollTarget ?: return null
        val child = scroll.getChildAt(0) ?: return null
        val content = child.height
        if (content <= scroll.height || height <= 0) return null
        val inset = 4f * density
        val track = height - inset * 2
        val minThumb = 28f * density
        val thumbH = max(minThumb, track * scroll.height / content.toFloat())
        val range = (content - scroll.height).toFloat()
        val travel = track - thumbH
        val top = inset + travel * (scroll.scrollY / range).coerceIn(0f, 1f)
        return top to (top + thumbH)
    }

    private fun scrollRange(scroll: ScrollView): Int {
        val child = scroll.getChildAt(0) ?: return 0
        return (child.height - scroll.height).coerceAtLeast(0)
    }
}
