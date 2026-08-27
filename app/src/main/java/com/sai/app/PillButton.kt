package com.sai.app

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/** A rounded, semi-transparent single-letter action button used in single-line headers; opacity follows AppTheme. */
object PillButton {
    fun create(context: Context, letter: String, onClick: () -> Unit): Button {
        val density = context.resources.displayMetrics.density
        return Button(context).apply {
            text = letter
            setTextColor(AppTheme.textPrimary)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 12f
            background = ContextCompat.getDrawable(context, R.drawable.pill_button_bg)?.mutate()?.apply {
                alpha = AppTheme.opacityAlpha(context)
            }
            minWidth = (40 * density).toInt()
            minHeight = 0
            minimumHeight = 0
            includeFontPadding = false
            elevation = 0f
            stateListAnimator = null
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            val margin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            margin.setMargins((4 * density).toInt(), 0, 0, 0)
            layoutParams = margin
            setOnClickListener { onClick() }
        }
    }
}
