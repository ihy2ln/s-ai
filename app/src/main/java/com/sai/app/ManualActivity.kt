package com.sai.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Built-in wiki-style user manual, loaded from bundled markdown assets. */
class ManualActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AppBackground.wrap(this, buildUi()))
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "S.Ai Manual"
            setTextColor(AppTheme.accentColor(this@ManualActivity))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 20f
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@ManualActivity, "N") { NavMenu.show(this@ManualActivity) })
        }

        val markdown = assets.open("manual.md").bufferedReader().use { it.readText() }
        val html = ManualRenderer.markdownToHtml(markdown)

        val webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                defaultTextEncodingName = "utf-8"
                builtInZoomControls = true
                displayZoomControls = false
            }
            loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(titleRow)
            addView(
                View(this@ManualActivity).apply {
                    setBackgroundColor(Color.rgb(50, 50, 55))
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                    topMargin = (8 * density).toInt()
                    bottomMargin = (8 * density).toInt()
                },
            )
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}
