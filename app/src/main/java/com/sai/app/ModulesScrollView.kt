package com.sai.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.ScrollView

/**
 * Home-screen module scroller.
 *
 * One-finger vertical drags are never intercepted, so knobs, pads, and step grids keep the gesture.
 * The page scrolls from the edge bar or a two-finger swipe.
 */
class ModulesScrollView(context: Context) : ScrollView(context) {

    var suppressIntercept = false

    private var twoFingerScrolling = false
    private var lastY = 0f

    init {
        isNestedScrollingEnabled = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount >= 2) {
                    beginTwoFinger(ev)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (twoFingerScrolling || ev.pointerCount >= 2) return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> twoFingerScrolling = false
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount >= 2) {
                    beginTwoFinger(ev)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!twoFingerScrolling && ev.pointerCount < 2) return false
                val y = currentY(ev)
                scrollBy(0, (lastY - y).toInt())
                lastY = y
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.pointerCount <= 2) {
                    twoFingerScrolling = false
                } else {
                    lastY = currentY(ev)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                twoFingerScrolling = false
                return true
            }
        }
        return false
    }

    private fun beginTwoFinger(ev: MotionEvent) {
        twoFingerScrolling = true
        lastY = currentY(ev)
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun currentY(ev: MotionEvent): Float {
        if (ev.pointerCount < 2) return ev.y
        return (ev.getY(0) + ev.getY(1)) / 2f
    }
}
