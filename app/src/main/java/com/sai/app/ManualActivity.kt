package com.sai.app

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.sai.core.wiki.WikiMarkdown

/** Built-in wiki: searchable, with a jump list of sections from the bundled markdown manual. */
class ManualActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var hitCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AppBackground.wrap(this, buildUi()))
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val accent = AppTheme.accentColor(this)
        val accentHex = "#%06X".format(accent and 0xFFFFFF)

        val markdown = assets.open("manual.md").bufferedReader().use { it.readText() }
        val sections = WikiMarkdown.sections(markdown)
        val html = WikiMarkdown.toHtml(markdown, accentHex)

        val title = Ui.screenTitle(this, "S.Ai Wiki", monospace = false)
        hitCount = TextView(this).apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            minWidth = (28 * density).toInt()
        }
        val search = Ui.input(this, hint = "Search this wiki").apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    findInPage(s?.toString().orEmpty())
                }
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    webView.findNext(true)
                    true
                } else {
                    false
                }
            }
        }

        val titleRow = Ui.headerBar(this) {
            addView(title, LinearLayout.LayoutParams((120 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * density).toInt()
                marginEnd = (8 * density).toInt()
            })
            addView(hitCount)
            addView(PillButton.create(this@ManualActivity, "N") { NavMenu.show(this@ManualActivity) })
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                defaultTextEncodingName = "utf-8"
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptEnabled = true
            }
            setFindListener { _, number, isDone ->
                if (isDone) {
                    val query = search.text?.toString().orEmpty()
                    hitCount.text = if (query.isBlank()) "" else number.toString()
                }
            }
            loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
        }

        val tocColumn = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(LinearLayout(this@ManualActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = androidx.core.content.ContextCompat.getDrawable(this@ManualActivity, R.drawable.module_card_bg)
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                addView(Ui.sectionLabel(this@ManualActivity, "Contents"))
                addView(sectionButton("Top", accent) { jumpTo(null) })
                for (section in sections) {
                    addView(sectionButton(section.title, accent) { jumpTo(section.slug) })
                }
            })
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                tocColumn,
                LinearLayout.LayoutParams((168 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = (8 * density).toInt()
                },
            )
            addView(
                webView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
            )
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(AppTheme.canvas)
            addView(titleRow)
            addView(Ui.divider(this@ManualActivity))
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun sectionButton(label: String, accent: Int, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            setTextColor(AppTheme.textPrimary)
            textSize = 12f
            setPadding((8 * density).toInt(), (7 * density).toInt(), (8 * density).toInt(), (7 * density).toInt())
            background = androidx.core.content.ContextCompat.getDrawable(this@ManualActivity, R.drawable.list_row_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
            setOnClickListener {
                setTextColor(accent)
                onClick()
            }
        }
    }

    private fun findInPage(query: String) {
        if (query.isBlank()) {
            webView.clearMatches()
            hitCount.text = ""
            return
        }
        webView.findAllAsync(query)
    }

    private fun jumpTo(slug: String?) {
        val js = if (slug == null) {
            "window.scrollTo(0,0);"
        } else {
            val safe = slug.replace("\\", "\\\\").replace("'", "\\'")
            """
            (function(){
              var el = document.getElementById('$safe');
              if (el) el.scrollIntoView({behavior:'smooth', block:'start'});
            })();
            """.trimIndent()
        }
        webView.evaluateJavascript(js, null)
    }

    companion object {
        private const val BASE_URL = "https://sai.local/manual"
    }
}
