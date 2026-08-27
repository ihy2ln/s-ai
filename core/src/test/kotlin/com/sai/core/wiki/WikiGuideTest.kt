package com.sai.core.wiki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File

class WikiGuideTest {

    private fun assetsDir(): File =
        File("../app/src/main/assets/wiki").takeIf { it.isDirectory } ?: File("app/src/main/assets/wiki")

    private fun docsDir(): File =
        File("../docs/wiki").takeIf { it.isDirectory } ?: File("docs/wiki")

    @Test
    fun `every topic has an in-app page and a docs mirror`() {
        val assets = assetsDir()
        val docs = docsDir()
        assertTrue(assets.isDirectory, "expected $assets")
        assertTrue(docs.isDirectory, "expected $docs")
        for (topic in WikiGuide.topics) {
            val asset = File(assets, "${topic.id}.md")
            val doc = File(docs, "${topic.id}.md")
            assertTrue(asset.isFile, "missing in-app page ${asset.path}")
            assertTrue(doc.isFile, "missing docs mirror ${doc.path}")
            assertEquals(asset.readText(), doc.readText(), "${topic.id}.md in-app and docs/wiki must match")
            val markdown = asset.readText()
            assertTrue(markdown.startsWith("# "), "${topic.id} should start with an H1")
            val html = WikiMarkdown.toHtml(markdown, includeToc = false)
            assertTrue(html.contains("<h1"), "missing h1 for ${topic.title}")
        }
    }

    @Test
    fun `guide pages only link to known topics`() {
        val assets = assetsDir()
        val link = Regex("\\[.+?]\\(([^)]+)\\)")
        for (topic in WikiGuide.topics) {
            val markdown = File(assets, "${topic.id}.md").readText()
            for (match in link.findAll(markdown)) {
                val href = WikiMarkdown.wikiHref(match.groupValues[1])
                if (href.startsWith("#")) continue
                if (href.startsWith("http")) continue
                val parsed = WikiMarkdown.parseWikiHref(href)
                assertNotNull(parsed, "unresolved link ${match.value} on ${topic.id}")
                val (page, _) = parsed
                assertTrue(page in WikiGuide.knownIds(), "${topic.id} links to unknown page $page")
            }
        }
    }

    @Test
    fun `required new-user topics are present`() {
        val ids = WikiGuide.knownIds()
        assertTrue("add-modules" in ids)
        assertTrue("instruments-effects-home" in ids)
        assertTrue("rack-vs-mixer" in ids)
        assertTrue("built-in-modules" in ids)
        assertTrue("jobs" in ids)
        assertTrue("not-a-vst-host" in ids)
        val jobs = File(assetsDir(), "jobs.md").readText().lowercase()
        assertTrue(jobs.contains("vocal"))
        assertTrue(jobs.contains("guitar"))
        assertTrue(jobs.contains("drum"))
        assertTrue(jobs.contains("mix"))
        val host = File(assetsDir(), "not-a-vst-host.md").readText().lowercase()
        assertTrue(host.contains(".vst3"))
        assertTrue(host.contains("not") && host.contains("host"))
    }
}
