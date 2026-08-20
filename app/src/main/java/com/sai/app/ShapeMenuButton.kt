package com.sai.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Shape-based header menu button; shows a text prompt for one second on press, then fades out. */
object ShapeMenuButton {

    enum class Shape { SQUARE, CIRCLE, DIAMOND, TRIANGLE }

    fun create(context: Context, prompt: String, shape: Shape, onClick: () -> Unit): FrameLayout {
        val density = context.resources.displayMetrics.density
        val size = (36 * density).toInt()

        val promptLabel = TextView(context).apply {
            text = prompt
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            alpha = 0f
            visibility = GONE
        }

        val icon = ShapeIconView(context, shape)

        return FrameLayout(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.pill_button_bg)?.mutate()?.apply {
                alpha = AppTheme.opacityAlpha(context)
            }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins((4 * density).toInt(), 0, 0, 0)
            }
            addView(icon, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(promptLabel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            isClickable = true
            setOnClickListener {
                promptLabel.visibility = VISIBLE
                promptLabel.alpha = 1f
                promptLabel.animate().cancel()
                promptLabel.postDelayed({
                    promptLabel.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction { promptLabel.visibility = GONE }
                        .start()
                }, 1000)
                onClick()
            }
        }
    }
}
