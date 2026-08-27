package com.sai.core.wiki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class WikiMarkdownTest {

    private val sample = """
        # S.Ai User Manual

        Welcome to **S.Ai**.

        ## Table of Contents

        - [Getting Started](#getting-started)
        - [Mixer](#mixer)

        ## Getting Started

        Import audio via **M → Samples**. Use `BPM 120`.

        See [Mixer](#mixer).

        ## Mixer

        | Control | Function |
        | --- | --- |
        | **FX** | Live insert |
        | **M** | Mute |

        ### Tap Tempo

        Tap twice.

        ---

        *S.Ai — your sounds.*
    """.trimIndent()

    @Test
    fun `sections skip the markdown table of contents`() {
        val sections = WikiMarkdown.sections(sample)
        assertEquals(listOf("Getting Started", "Mixer"), sections.map { it.title })
        assertEquals("getting-started", sections[0].slug)
        assertEquals("mixer", sections[1].slug)
    }

    @Test
    fun `slug strips punctuation and collapses spaces`() {
        assertEquals(
            "menu-buttons-e-n-mx-p-m",
            WikiMarkdown.slug("Menu Buttons (E / N / MX / P / M)"),
        )
        assertEquals("effects-mx", WikiMarkdown.slug("Effects (MX)"))
    }

    @Test
    fun `html builds a jumpable generated toc and skips the markdown toc list`() {
        val html = WikiMarkdown.toHtml(sample, accent = "#00ffff")
        assertTrue(html.contains("""<div class="toc">"""))
        assertTrue(html.contains("""<a href="#getting-started">Getting Started</a>"""))
        assertTrue(html.contains("""<h2 id="getting-started">"""))
        assertTrue(html.contains("""<h2 id="mixer">"""))
        assertTrue(html.contains("""<h3 id="tap-tempo">"""))
        assertFalse(html.contains("Table of Contents"))
        assertFalse(html.contains("[Getting Started](#getting-started)"))
        assertTrue(html.contains("<strong>S.Ai</strong>"))
        assertTrue(html.contains("<code>BPM 120</code>"))
        assertTrue(html.contains("""<a href="#mixer">Mixer</a>"""))
        assertTrue(html.contains("<th>"))
        assertTrue(html.contains("<td>"))
        assertTrue(html.contains("<hr/>"))
        assertTrue(html.contains("<em>S.Ai — your sounds.</em>"))
        assertTrue(html.contains("#00ffff"))
    }

    @Test
    fun `page links become wiki hrefs and hash links stay in-page`() {
        assertEquals("#mixer", WikiMarkdown.wikiHref("#mixer"))
        assertEquals("${WikiMarkdown.WIKI_PREFIX}add-modules", WikiMarkdown.wikiHref("add-modules.md"))
        assertEquals("${WikiMarkdown.WIKI_PREFIX}add-modules#search", WikiMarkdown.wikiHref("add-modules.md#search"))
        assertEquals("https://example.com/x", WikiMarkdown.wikiHref("https://example.com/x"))
        assertEquals("add-modules" to null, WikiMarkdown.parseWikiHref(WikiMarkdown.wikiHref("add-modules.md")))
        assertEquals("add-modules" to "search", WikiMarkdown.parseWikiHref(WikiMarkdown.wikiHref("add-modules.md#search")))
        val html = WikiMarkdown.toHtml("See [Add a module](add-modules.md).", includeToc = false)
        assertTrue(html.contains("""href="${WikiMarkdown.WIKI_PREFIX}add-modules""""))
        assertFalse(html.contains("""<div class="toc">"""))
    }

    @Test
    fun `blockquotes render as callouts`() {
        val html = WikiMarkdown.toHtml("> Not a native host.", includeToc = false)
        assertTrue(html.contains("<blockquote>Not a native host.</blockquote>"))
    }

    @Test
    fun `generated toc links match heading ids`() {
        val html = WikiMarkdown.toHtml(sample)
        for (section in WikiMarkdown.sections(sample)) {
            assertTrue(html.contains("""href="#${section.slug}""""), "missing toc link for ${section.slug}")
            assertTrue(html.contains("""id="${section.slug}""""), "missing heading id for ${section.slug}")
        }
    }

    @Test
    fun `project manual headings all appear as wiki targets`() {
        val file = File("../docs/MANUAL.md").takeIf { it.isFile } ?: File("docs/MANUAL.md")
        assertTrue(file.isFile, "docs/MANUAL.md should be readable from the test working directory")
        val markdown = file.readText()
        val html = WikiMarkdown.toHtml(markdown)
        val sections = WikiMarkdown.sections(markdown)
        assertTrue(sections.size >= 10, "manual should have many wiki sections")
        for (section in sections) {
            assertTrue(html.contains("""id="${section.slug}""""), "missing id for ${section.title}")
            assertTrue(html.contains("""href="#${section.slug}""""), "missing toc href for ${section.title}")
        }
    }
}
