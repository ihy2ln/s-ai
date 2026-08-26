package com.sai.core.wiki

/**
 * Wiki-style markdown for the bundled user manual: headings become jump targets,
 * a table of contents is generated from H2s, and GitHub-style `#slug` links resolve.
 */
object WikiMarkdown {

    data class Section(val title: String, val slug: String)

    fun sections(markdown: String): List<Section> =
        markdown.lineSequence()
            .map { it.trimEnd() }
            .filter { it.startsWith("## ") }
            .map { it.removePrefix("## ").trim() }
            .filter { it.isNotEmpty() && !it.equals("Table of Contents", ignoreCase = true) }
            .map { Section(it, slug(it)) }
            .toList()

    fun slug(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")

    fun toHtml(markdown: String, accent: String = "#4dd0e1"): String {
        val sections = sections(markdown)
        val body = StringBuilder()
        body.append(tocHtml(sections))
        appendBody(markdown, body)
        return wrapHtml(body.toString(), accent)
    }

    private fun tocHtml(sections: List<Section>): String {
        if (sections.isEmpty()) return ""
        val items = sections.joinToString("") { section ->
            """<li><a href="#${section.slug}">${escape(section.title)}</a></li>"""
        }
        return """<div class="toc"><div class="toc-title">Contents</div><ul>$items</ul></div>"""
    }

    private fun appendBody(markdown: String, body: StringBuilder) {
        var inList = false
        var inTable = false
        var skipToc = false

        fun closeList() {
            if (inList) {
                body.append("</ul>")
                inList = false
            }
        }

        fun closeTable() {
            if (inTable) {
                body.append("</table>")
                inTable = false
            }
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (skipToc) {
                if (line.startsWith("## ") && !line.removePrefix("## ").equals("Table of Contents", ignoreCase = true)) {
                    skipToc = false
                } else {
                    return@forEach
                }
            }
            when {
                line.startsWith("## ") && line.removePrefix("## ").equals("Table of Contents", ignoreCase = true) -> {
                    closeList()
                    closeTable()
                    skipToc = true
                }
                line.startsWith("# ") -> {
                    closeList()
                    closeTable()
                    val title = line.removePrefix("# ")
                    body.append("<h1 id=\"").append(slug(title)).append("\">")
                    body.append(inline(title)).append("</h1>")
                }
                line.startsWith("## ") -> {
                    closeList()
                    closeTable()
                    val title = line.removePrefix("## ")
                    body.append("<h2 id=\"").append(slug(title)).append("\">")
                    body.append(inline(title)).append("</h2>")
                }
                line.startsWith("### ") -> {
                    closeList()
                    closeTable()
                    val title = line.removePrefix("### ")
                    body.append("<h3 id=\"").append(slug(title)).append("\">")
                    body.append(inline(title)).append("</h3>")
                }
                line.startsWith("---") && line.all { it == '-' } -> {
                    closeList()
                    closeTable()
                    body.append("<hr/>")
                }
                line.startsWith("- ") -> {
                    closeTable()
                    if (!inList) {
                        body.append("<ul>")
                        inList = true
                    }
                    body.append("<li>").append(inline(line.removePrefix("- "))).append("</li>")
                }
                line.startsWith("|") && line.endsWith("|") -> {
                    closeList()
                    val cells = line.trim('|').split('|').map { it.trim() }
                    if (!inTable) {
                        body.append("<table>")
                        inTable = true
                        body.append("<tr>")
                        cells.forEach { cell ->
                            body.append("<th>").append(inline(cell)).append("</th>")
                        }
                        body.append("</tr>")
                    } else if (cells.all { it.all { ch -> ch == '-' || ch == ':' || ch == ' ' } }) {
                        // separator row
                    } else {
                        body.append("<tr>")
                        cells.forEach { cell ->
                            body.append("<td>").append(inline(cell)).append("</td>")
                        }
                        body.append("</tr>")
                    }
                }
                line.isBlank() -> {
                    closeList()
                    closeTable()
                }
                else -> {
                    closeList()
                    closeTable()
                    body.append("<p>").append(inline(line)).append("</p>")
                }
            }
        }
        closeList()
        closeTable()
    }

    private fun inline(text: String): String {
        var out = escape(text)
        out = out.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        out = out.replace(Regex("`(.+?)`"), "<code>$1</code>")
        out = out.replace(Regex("\\[(.+?)\\]\\(#(.+?)\\)")) { match ->
            val label = match.groupValues[1]
            val target = match.groupValues[2]
            """<a href="#$target">$label</a>"""
        }
        out = out.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        return out
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun wrapHtml(body: String, accent: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1"/>
          <style>
            html { scroll-behavior: smooth; }
            body {
              background: #121214;
              color: #e8e8ea;
              font-family: sans-serif;
              font-size: 15px;
              line-height: 1.55;
              padding: 8px 16px 48px;
              margin: 0;
            }
            h1 {
              color: $accent;
              font-size: 1.6em;
              border-bottom: 1px solid #2a2a30;
              padding-bottom: 8px;
              margin-top: 0;
            }
            h2 {
              color: #81d4fa;
              font-size: 1.25em;
              margin-top: 28px;
              border-left: 3px solid $accent;
              padding-left: 10px;
            }
            h3 {
              color: #b0bec5;
              font-size: 1.05em;
              margin-top: 18px;
            }
            p { margin: 8px 0; }
            ul { margin: 8px 0 8px 8px; padding-left: 18px; }
            li { margin: 4px 0; }
            hr { border: 0; border-top: 1px solid #2a2a30; margin: 24px 0; }
            em { color: #b0bec5; }
            code {
              background: #1e1e24;
              color: #ffcc80;
              padding: 1px 5px;
              border-radius: 3px;
              font-family: monospace;
              font-size: 0.92em;
            }
            strong { color: #ffffff; }
            table {
              width: 100%;
              border-collapse: collapse;
              margin: 12px 0;
              font-size: 0.92em;
            }
            th, td {
              border: 1px solid #333;
              padding: 6px 8px;
              text-align: left;
            }
            th { background: #1a1a20; color: $accent; }
            a { color: #64b5f6; text-decoration: none; }
            .toc {
              background: #1a1a22;
              border: 1px solid #2e2e36;
              border-radius: 8px;
              padding: 12px 14px;
              margin: 8px 0 20px;
            }
            .toc-title { color: $accent; font-weight: bold; margin-bottom: 8px; }
            .toc ul { margin: 0; padding-left: 18px; }
          </style>
        </head>
        <body>
        $body
        </body>
        </html>
    """.trimIndent()
}
