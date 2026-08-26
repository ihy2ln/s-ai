package com.sai.core.project

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Files inside a `.sai` project package (zip). JSON-only saves remain valid to load. */
data class ProjectArchive(
    val projectJson: String,
    val libraryJson: String = "{}",
    val rackJson: String = "{}",
    val mixerJson: String = "{}",
    val layoutJson: String = "{}",
    val themeJson: String = "{}",
    val backgroundJson: String = "{}",
    val padsJson: String = "{}",
    val playlistJson: String = "{}",
    val samples: Map<String, ByteArray> = emptyMap(),
    val backgroundBytes: ByteArray? = null,
    val backgroundName: String? = null,
)

object ProjectPack {
    const val FORMAT = "sai-project"
    const val VERSION = 1

    const val MANIFEST = "manifest.json"
    const val PROJECT = "project.json"
    const val LIBRARY = "library.json"
    const val RACK = "rack.json"
    const val MIXER = "mixer.json"
    const val LAYOUT = "layout.json"
    const val THEME = "theme.json"
    const val BACKGROUND = "background.json"
    const val PADS = "pads.json"
    const val PLAYLIST = "playlist.json"
    const val SAMPLES_DIR = "samples/"
    const val BACKGROUND_FILE = "background.bin"

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    fun write(archive: ProjectArchive): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, MANIFEST, """{"format":"$FORMAT","version":$VERSION}""".toByteArray(Charsets.UTF_8))
            put(zip, PROJECT, archive.projectJson.toByteArray(Charsets.UTF_8))
            put(zip, LIBRARY, archive.libraryJson.toByteArray(Charsets.UTF_8))
            put(zip, RACK, archive.rackJson.toByteArray(Charsets.UTF_8))
            put(zip, MIXER, archive.mixerJson.toByteArray(Charsets.UTF_8))
            put(zip, LAYOUT, archive.layoutJson.toByteArray(Charsets.UTF_8))
            put(zip, THEME, archive.themeJson.toByteArray(Charsets.UTF_8))
            put(zip, BACKGROUND, archive.backgroundJson.toByteArray(Charsets.UTF_8))
            put(zip, PADS, archive.padsJson.toByteArray(Charsets.UTF_8))
            put(zip, PLAYLIST, archive.playlistJson.toByteArray(Charsets.UTF_8))
            for ((name, bytes) in archive.samples) {
                val path = if (name.startsWith(SAMPLES_DIR)) name else SAMPLES_DIR + name
                put(zip, path, bytes)
            }
            val background = archive.backgroundBytes
            if (background != null && background.isNotEmpty()) {
                put(zip, archive.backgroundName ?: BACKGROUND_FILE, background)
            }
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): ProjectArchive {
        var projectJson = "{}"
        var libraryJson = "{}"
        var rackJson = "{}"
        var mixerJson = "{}"
        var layoutJson = "{}"
        var themeJson = "{}"
        var backgroundJson = "{}"
        var padsJson = "{}"
        var playlistJson = "{}"
        val samples = mutableMapOf<String, ByteArray>()
        var backgroundBytes: ByteArray? = null
        var backgroundName: String? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimStart('/')
                val data = zip.readBytes()
                zip.closeEntry()
                when {
                    name == MANIFEST -> { /* format/version; ignored for now */ }
                    name == PROJECT -> projectJson = data.toString(Charsets.UTF_8)
                    name == LIBRARY -> libraryJson = data.toString(Charsets.UTF_8)
                    name == RACK -> rackJson = data.toString(Charsets.UTF_8)
                    name == MIXER -> mixerJson = data.toString(Charsets.UTF_8)
                    name == LAYOUT -> layoutJson = data.toString(Charsets.UTF_8)
                    name == THEME -> themeJson = data.toString(Charsets.UTF_8)
                    name == BACKGROUND -> backgroundJson = data.toString(Charsets.UTF_8)
                    name == PADS -> padsJson = data.toString(Charsets.UTF_8)
                    name == PLAYLIST -> playlistJson = data.toString(Charsets.UTF_8)
                    name.startsWith(SAMPLES_DIR) && !entry.isDirectory -> {
                        val file = name.removePrefix(SAMPLES_DIR)
                        if (file.isNotBlank()) samples[file] = data
                    }
                    name == BACKGROUND_FILE || name.startsWith("background.") -> {
                        backgroundBytes = data
                        backgroundName = name
                    }
                }
            }
        }
        return ProjectArchive(
            projectJson = projectJson,
            libraryJson = libraryJson,
            rackJson = rackJson,
            mixerJson = mixerJson,
            layoutJson = layoutJson,
            themeJson = themeJson,
            backgroundJson = backgroundJson,
            padsJson = padsJson,
            playlistJson = playlistJson,
            samples = samples,
            backgroundBytes = backgroundBytes,
            backgroundName = backgroundName,
        )
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}
