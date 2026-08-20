package com.sai.app

import android.content.Context
import android.view.MotionEvent
import android.widget.ScrollView

/** Home-screen module scroller that never steals divider-bar drags. */
class ModulesScrollView(context: Context) : ScrollView(context) {

    var suppressIntercept = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            suppressIntercept = dividerWouldHandle(ev)
        }
        if (suppressIntercept) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (suppressIntercept) return false
        return super.onTouchEvent(ev)
    }

    private fun dividerWouldHandle(ev: MotionEvent): Boolean {
        val stack = getChildAt(0) as? ModuleStackView ?: return false
        val yInStack = ev.y + scrollY - stack.top
        return stack.wouldHandle(yInStack)
    }
}
