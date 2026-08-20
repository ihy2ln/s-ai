package com.sai.app

import android.view.View
import android.view.ViewParent

/** Keeps the home-screen module scroller from stealing drags meant for knobs, pads, and steps. */
object TouchScrollGuard {

    private var lockDepth = 0

    fun lock(view: View) {
        lockDepth++
        if (lockDepth == 1) {
            applyInterceptLock(view, lock = true)
        }
    }

    fun unlock(view: View) {
        if (lockDepth <= 0) return
        lockDepth--
        if (lockDepth == 0) {
            applyInterceptLock(view, lock = false)
        }
    }

    private fun applyInterceptLock(view: View, lock: Boolean) {
        var parent: ViewParent? = view.parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(lock)
            if (parent is ModulesScrollView) {
                parent.suppressIntercept = lock
                break
            }
            parent = parent.parent
        }
    }
}
