package com.sai.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout

/**
 * Vertical stack of modules and divider bars. Touches on a divider are captured here so nested
 * ScrollViews cannot steal the gesture, and dragging resizes only the module above that bar.
 */
class ModuleStackView(context: Context) : LinearLayout(context) {

    var onResizeStart: (() -> Unit)? = null
    var onResizeEnd: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val minHeightPx = (64 * density).toInt()
    private val maxHeightPx = (4000 * density).toInt()
    private val extraHitPx = (12 * density).toInt()

    private var draggingModule = -1
    private var lastRawY = 0f

    init {
        orientation = VERTICAL
        isMotionEventSplittingEnabled = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val module = moduleIndexForDivider(ev.y)
                if (module >= 0) {
                    beginDrag(module, ev.rawY)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingModule >= 0) return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val module = moduleIndexForDivider(ev.y)
                if (module >= 0) {
                    beginDrag(module, ev.rawY)
                    return true
                }
                return draggingModule >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingModule < 0) return false
                val dy = ev.rawY - lastRawY
                lastRawY = ev.rawY
                resizeModule(draggingModule, dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingModule < 0) return false
                endDrag()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun beginDrag(moduleIndex: Int, rawY: Float) {
        draggingModule = moduleIndex
        lastRawY = rawY
        parent?.requestDisallowInterceptTouchEvent(true)
        (parent as? ModulesScrollView)?.suppressIntercept = true
        onResizeStart?.invoke()
    }

    private fun endDrag() {
        draggingModule = -1
        parent?.requestDisallowInterceptTouchEvent(false)
        (parent as? ModulesScrollView)?.suppressIntercept = false
        onResizeEnd?.invoke()
    }

    private fun resizeModule(moduleIndex: Int, deltaPx: Float) {
        val wrapper = getChildAt(moduleIndex * 2) ?: return
        val current = when {
            wrapper.height > 0 -> wrapper.height
            wrapper.layoutParams.height > 0 -> wrapper.layoutParams.height
            else -> minHeightPx
        }
        val newHeight = (current + deltaPx).toInt().coerceIn(minHeightPx, maxHeightPx)
        if (newHeight == current) return
        wrapper.layoutParams = wrapper.layoutParams.apply { height = newHeight }
    }

    /** True if a divider bar sits under [y] (this view's coordinates). */
    fun wouldHandle(y: Float): Boolean = moduleIndexForDivider(y) >= 0

    private fun moduleIndexForDivider(y: Float): Int {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !is ResizeHandleView) continue
            val top = child.top - extraHitPx
            val bottom = child.bottom + extraHitPx
            if (y >= top && y <= bottom) return i / 2
        }
        return -1
    }
}
