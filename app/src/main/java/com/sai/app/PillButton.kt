package com.sai.app

import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/** A rounded, semi-transparent (60%) single-letter action button used in single-line headers. */
object PillButton {
    fun create(context: Context, letter: String, onClick: () -> Unit): Button {
        val density = context.resources.displayMetrics.density
        return Button(context).apply {
            text = letter
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.pill_button_bg)
            minWidth = (44 * density).toInt()
            minHeight = 0
            setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
            val margin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            margin.setMargins((4 * density).toInt(), 0, 0, 0)
            layoutParams = margin
            setOnClickListener { onClick() }
        }
    }
}
