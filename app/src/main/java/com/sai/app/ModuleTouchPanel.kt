package com.sai.app

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/** Wraps module content so touches on knobs, pads, and grids stay inside the module instead of scrolling the page. */
class ModuleTouchPanel(context: Context) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchScrollGuard.lock(this)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> TouchScrollGuard.unlock(this)
        }
        return super.dispatchTouchEvent(ev)
    }
}
