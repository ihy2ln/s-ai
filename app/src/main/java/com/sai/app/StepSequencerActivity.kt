package com.sai.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Full-screen wrapper around [StepSequencerPanelView] - the same panel also embeds directly
 *  into the Home screen as the STEP SEQ module. */
class StepSequencerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AppBackground.wrap(this, buildUi()))
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "STEP SEQUENCER"
            setTextColor(AppTheme.accentColor(this@StepSequencerActivity))
            typeface = Typeface.MONOSPACE
            textSize = 18f
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@StepSequencerActivity, "N") { onBackPressedDispatcher.onBackPressed() })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(titleRow)
            addView(
                StepSequencerPanelView(this@StepSequencerActivity),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }
}
