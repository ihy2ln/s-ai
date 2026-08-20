package com.sai.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Colored shape transport control; shows a text prompt for one second on press, then fades out. */
class TransportShapeButtonView(
    context: Context,
    private val promptLabel: TextView,
    private val iconView: View,
) : FrameLayout(context) {

    fun setAppearance(shape: ShapeIconView.Shape, color: Int, prompt: String) {
        promptLabel.text = prompt
        (iconView as? ShapeIconView)?.apply {
            this.shape = shape
            iconColor = color
        }
    }

    fun flashPrompt() {
        promptLabel.visibility = View.VISIBLE
        promptLabel.alpha = 1f
        promptLabel.animate().cancel()
        promptLabel.postDelayed({
            promptLabel.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { promptLabel.visibility = View.GONE }
                .start()
        }, 1000)
    }
}

object TransportShapeButton {

    val PLAY_GREEN = Color.rgb(76, 217, 100)
    val STOP_WHITE = Color.WHITE
    val RECORD_RED = Color.rgb(224, 40, 40)
    val RECORD_ARMED = Color.rgb(255, 70, 70)
    val EDIT_BLUE = Color.rgb(66, 133, 244)
    val TAP_YELLOW = Color.rgb(255, 204, 0)

    fun createTempo(context: Context, onClick: () -> Unit): TransportShapeButtonView {
        val density = context.resources.displayMetrics.density
        val size = (36 * density).toInt()

        val promptLabel = TextView(context).apply {
            text = "Tap"
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            alpha = 0f
            visibility = View.GONE
        }

        val iconView = TempoIconView(context)

        return TransportShapeButtonView(context, promptLabel, iconView).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins((3 * density).toInt(), 0, (3 * density).toInt(), 0)
            }
            addView(iconView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(promptLabel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            isClickable = true
            setOnClickListener {
                flashPrompt()
                onClick()
            }
        }
    }

    fun create(
        context: Context,
        prompt: String,
        shape: ShapeIconView.Shape,
        color: Int,
        big: Boolean = false,
        onClick: () -> Unit,
    ): TransportShapeButtonView {
        val density = context.resources.displayMetrics.density
        val size = ((if (big) 40 else 36) * density).toInt()

        val promptLabel = TextView(context).apply {
            text = prompt
            setTextColor(Color.WHITE)
            textSize = if (big) 11f else 10f
            gravity = Gravity.CENTER
            alpha = 0f
            visibility = View.GONE
        }

        val iconView = ShapeIconView(context).apply {
            this.shape = shape
            iconColor = color
        }

        return TransportShapeButtonView(context, promptLabel, iconView).apply {
            background = ContextCompat.getDrawable(context, R.drawable.pill_button_bg)?.mutate()?.apply {
                alpha = AppTheme.opacityAlpha(context)
            }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins((3 * density).toInt(), 0, (3 * density).toInt(), 0)
            }
            addView(iconView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(promptLabel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            isClickable = true
            setOnClickListener {
                flashPrompt()
                onClick()
            }
        }
    }
}
