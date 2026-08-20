package com.sai.app

/** Converts the bundled markdown manual into styled HTML for in-app display. */
object ManualRenderer {

    fun markdownToHtml(markdown: String): String {
        val body = StringBuilder()
        var inList = false
        var inTable = false

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
            when {
                line.startsWith("# ") -> {
                    closeList()
                    closeTable()
                    body.append("<h1 id=\"").append(slug(line.removePrefix("# "))).append("\">")
                    body.append(inline(line.removePrefix("# ")))
                    body.append("</h1>")
                }
                line.startsWith("## ") -> {
                    closeList()
                    closeTable()
                    val title = line.removePrefix("## ")
                    body.append("<h2 id=\"").append(slug(title)).append("\">")
                    body.append(inline(title))
                    body.append("</h2>")
                }
                line.startsWith("### ") -> {
                    closeList()
                    closeTable()
                    body.append("<h3>").append(inline(line.removePrefix("### "))).append("</h3>")
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
                        // separator row — skip
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
        return wrapHtml(body.toString())
    }

    private fun slug(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun inline(text: String): String {
        var out = escape(text)
        out = out.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        out = out.replace(Regex("`(.+?)`"), "<code>$1</code>")
        out = out.replace(Regex("\\[(.+?)\\]\\(#(.+?)\\)"), "<a href=\"#\$2\">\$1</a>")
        return out
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun wrapHtml(body: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1"/>
          <style>
            body {
              background: #121214;
              color: #e8e8ea;
              font-family: sans-serif;
              font-size: 15px;
              line-height: 1.55;
              padding: 12px 16px 32px;
              margin: 0;
            }
            h1 {
              color: #4dd0e1;
              font-size: 1.6em;
              border-bottom: 1px solid #2a2a30;
              padding-bottom: 8px;
              margin-top: 0;
            }
            h2 {
              color: #81d4fa;
              font-size: 1.25em;
              margin-top: 28px;
              border-left: 3px solid #4dd0e1;
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
            th { background: #1a1a20; color: #4dd0e1; }
            a { color: #64b5f6; text-decoration: none; }
            .toc {
              background: #1a1a22;
              border: 1px solid #2e2e36;
              border-radius: 8px;
              padding: 12px 14px;
              margin: 12px 0 20px;
            }
            .toc-title { color: #4dd0e1; font-weight: bold; margin-bottom: 8px; }
          </style>
        </head>
        <body>
        $body
        </body>
        </html>
    """.trimIndent()
}
