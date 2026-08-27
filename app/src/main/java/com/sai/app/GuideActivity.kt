package com.sai.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.sai.core.wiki.WikiGuide
import com.sai.core.wiki.WikiMarkdown

/** New-user wiki: short linked topics from `assets/wiki/`. Opened via ?, M → Guide, N → Guide. */
class GuideActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var hitCount: TextView
    private lateinit var topicColumn: LinearLayout
    private var currentId: String = WikiGuide.INDEX_ID
    private var pendingSlug: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentId = intent.getStringExtra(EXTRA_PAGE)?.takeIf { WikiGuide.byId(it) != null }
            ?: WikiGuide.INDEX_ID
        pendingSlug = intent.getStringExtra(EXTRA_SLUG)
        setContentView(AppBackground.wrap(this, buildUi()))
        loadPage(currentId, pendingSlug)
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val accent = AppTheme.accentColor(this)

        val title = TextView(this).apply {
            text = "Guide"
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 18f
        }
        hitCount = TextView(this).apply {
            setTextColor(Color.rgb(140, 150, 160))
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            minWidth = (28 * density).toInt()
        }
        val search = EditText(this).apply {
            hint = "Search this guide"
            setHintTextColor(Color.rgb(90, 96, 104))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(32, 34, 40))
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
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

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams((88 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * density).toInt()
                marginEnd = (8 * density).toInt()
            })
            addView(hitCount)
            addView(PillButton.create(this@GuideActivity, "N") { NavMenu.show(this@GuideActivity) })
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
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return handleHref(request.url.toString())
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return handleHref(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    pendingSlug?.let { slug ->
                        jumpTo(slug)
                        pendingSlug = null
                    }
                }
            }
        }

        topicColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rebuildTopicRail(accent)

        val tocColumn = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(LinearLayout(this@GuideActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@GuideActivity).apply {
                    text = "TOPICS"
                    setTextColor(accent)
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setPadding(0, 0, 0, (6 * density).toInt())
                })
                addView(topicColumn)
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
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(titleRow)
            addView(
                View(this@GuideActivity).apply {
                    setBackgroundColor(Color.rgb(50, 50, 55))
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                    topMargin = (8 * density).toInt()
                    bottomMargin = (8 * density).toInt()
                },
            )
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun rebuildTopicRail(accent: Int) {
        topicColumn.removeAllViews()
        for (topic in WikiGuide.topics) {
            topicColumn.addView(topicButton(topic.title, topic.id == currentId, accent) {
                loadPage(topic.id)
            })
        }
    }

    private fun topicButton(label: String, selected: Boolean, accent: Int, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            setTextColor(if (selected) accent else Color.WHITE)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = 12f
            setPadding((8 * density).toInt(), (7 * density).toInt(), (8 * density).toInt(), (7 * density).toInt())
            setBackgroundColor(if (selected) Color.rgb(28, 36, 42) else Color.rgb(32, 34, 40))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun loadPage(id: String, slug: String? = null) {
        val topic = WikiGuide.byId(id) ?: WikiGuide.topics.first()
        currentId = topic.id
        pendingSlug = slug
        val markdown = assets.open(WikiGuide.assetPath(topic.id)).bufferedReader().use { it.readText() }
        val accentHex = "#%06X".format(AppTheme.accentColor(this) and 0xFFFFFF)
        val html = WikiMarkdown.toHtml(markdown, accentHex, includeToc = false)
        webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
        rebuildTopicRail(AppTheme.accentColor(this))
    }

    private fun handleHref(href: String): Boolean {
        val parsed = WikiMarkdown.parseWikiHref(href)
        if (parsed != null) {
            val (page, slug) = parsed
            if (WikiGuide.byId(page) != null) {
                loadPage(page, slug)
                return true
            }
        }
        if (href.startsWith("#")) {
            jumpTo(href.removePrefix("#"))
            return true
        }
        return false
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
        val js = if (slug.isNullOrBlank()) {
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
        const val EXTRA_PAGE = "page"
        const val EXTRA_SLUG = "slug"
        private const val BASE_URL = "https://sai.local/wiki/"

        fun open(context: Context, page: String = WikiGuide.INDEX_ID, slug: String? = null) {
            context.startActivity(
                Intent(context, GuideActivity::class.java)
                    .putExtra(EXTRA_PAGE, page)
                    .apply {
                        if (!slug.isNullOrBlank()) putExtra(EXTRA_SLUG, slug)
                    },
            )
        }
    }
}
