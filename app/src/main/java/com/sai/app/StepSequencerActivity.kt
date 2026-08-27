package com.sai.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Full-screen wrapper around [ChannelRackPanelView] - the same panel also embeds directly
 *  into the Home screen as the CHANNEL RACK module. */
class StepSequencerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AppBackground.wrap(this, buildUi()))
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "CHANNEL RACK"
            setTextColor(AppTheme.accentColor(this@StepSequencerActivity))
            textSize = 18f
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@StepSequencerActivity, "N") { NavMenu.show(this@StepSequencerActivity) })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(titleRow)
            addView(
                ChannelRackPanelView(this@StepSequencerActivity).also { panel ->
                    panel.onAddPlugin = {
                        ModuleAddFlow.showBrowser(
                            context = this@StepSequencerActivity,
                            title = "Add Module",
                            initialRole = com.sai.core.plugin.PluginRole.INSTRUMENT,
                            onRackChanged = { panel.syncFromStore() },
                        )
                    }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }
}
