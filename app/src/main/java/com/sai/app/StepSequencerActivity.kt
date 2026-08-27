package com.sai.app

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import androidx.activity.ComponentActivity

/** Full-screen wrapper around [ChannelRackPanelView] - the same panel also embeds directly
 *  into the Home screen as the CHANNEL RACK module. */
class StepSequencerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AppBackground.wrap(this, buildUi()))
    }

    private fun buildUi(): LinearLayout {
        val pad = Ui.dp(this, 12f)

        val title = Ui.screenTitle(this, "CHANNEL RACK")
        val titleRow = Ui.headerBar(this) {
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@StepSequencerActivity, "N") { NavMenu.show(this@StepSequencerActivity) })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(AppTheme.canvas)
            addView(titleRow)
            addView(
                ChannelRackPanelView(this@StepSequencerActivity),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }
}
