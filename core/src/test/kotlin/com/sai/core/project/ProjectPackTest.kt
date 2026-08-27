package com.sai.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectPackTest {

    @Test
    fun `round-trips json files and sample bytes`() {
        val archive = ProjectArchive(
            projectJson = """{"name":"Demo","bpm":140}""",
            libraryJson = """{"entries":[{"id":1,"file":"kick.wav"}]}""",
            rackJson = """{"visibleCount":4}""",
            mixerJson = """{"master":1}""",
            layoutJson = """{"entries":[]}""",
            themeJson = """{"accent":1}""",
            backgroundJson = """{"type":"color"}""",
            padsJson = """{"pads":[]}""",
            playlistJson = """{"clips":[]}""",
            pluginsJson = """{"vst3.delay":"{\"mix\":0.3}"}""",
            samples = mapOf("kick.wav" to byteArrayOf(1, 2, 3, 4)),
            backgroundBytes = byteArrayOf(9, 9),
            backgroundName = "background.bin",
        )
        val packed = ProjectPack.write(archive)
        assertTrue(ProjectPack.isZip(packed))
        val read = ProjectPack.read(packed)
        assertEquals(archive.projectJson, read.projectJson)
        assertEquals(archive.libraryJson, read.libraryJson)
        assertEquals(archive.rackJson, read.rackJson)
        assertEquals(archive.mixerJson, read.mixerJson)
        assertEquals(archive.layoutJson, read.layoutJson)
        assertEquals(archive.themeJson, read.themeJson)
        assertEquals(archive.backgroundJson, read.backgroundJson)
        assertEquals(archive.padsJson, read.padsJson)
        assertEquals(archive.playlistJson, read.playlistJson)
        assertEquals(archive.pluginsJson, read.pluginsJson)
        assertTrue(read.samples["kick.wav"].contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(read.backgroundBytes.contentEquals(byteArrayOf(9, 9)))
    }

    @Test
    fun `plain json is not a zip`() {
        assertFalse(ProjectPack.isZip("""{"bpm":120}""".toByteArray()))
    }
}
