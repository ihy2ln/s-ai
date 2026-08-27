package com.sai.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sai.core.audio.WavIO
import com.sai.core.project.ProjectArchive
import com.sai.core.project.ProjectPack
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Packs / unpacks a full S.Ai project: song, samples, rack, mixer, layout, and theme. */
object ProjectBundle {

    fun export(context: Context, moduleEntries: List<ModuleEntry>): ByteArray {
        val library = SampleLibrary(context)
        val entries = library.all()
        val samples = mutableMapOf<String, ByteArray>()
        val libraryArray = JSONArray()
        for (entry in entries) {
            val file = "${entry.id}.wav"
            val bytes = sampleBytes(context, entry) ?: continue
            samples[file] = bytes
            libraryArray.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.displayName)
                    .put("category", entry.category)
                    .put("tags", entry.tags)
                    .put("file", file),
            )
        }
        val backgroundBytes = backgroundBytes(context)
        return ProjectPack.write(
            ProjectArchive(
                projectJson = TrackerProjectStore.get(context).exportProjectJson(),
                libraryJson = JSONObject().put("entries", libraryArray).toString(),
                rackJson = ChannelRackStore.exportJson(context),
                mixerJson = MixerStore.exportJson(context),
                layoutJson = ModuleLayoutStore.exportJson(context, moduleEntries),
                themeJson = AppTheme.exportJson(context),
                backgroundJson = AppBackground.exportJson(context),
                padsJson = PadBankStore.exportJson(context),
                playlistJson = PlaylistStore.exportJson(context),
                pluginsJson = PluginParamStore.exportJson(context),
                samples = samples,
                backgroundBytes = backgroundBytes,
                backgroundName = if (backgroundBytes != null) ProjectPack.BACKGROUND_FILE else null,
            ),
        )
    }

    fun import(context: Context, bytes: ByteArray): ImportResult {
        if (!ProjectPack.isZip(bytes)) {
            TrackerProjectStore.get(context).importProjectJson(bytes.toString(Charsets.UTF_8))
            return ImportResult(layout = null, jsonOnly = true)
        }
        val archive = ProjectPack.read(bytes)
        TrackerProjectStore.get(context).importProjectJson(archive.projectJson)
        importLibrary(context, archive)
        ChannelRackStore.importJson(context, archive.rackJson)
        MixerStore.importJson(context, archive.mixerJson)
        AppTheme.importJson(context, archive.themeJson)
        val bundledBackground = writeBundledFile(context, archive.backgroundName ?: ProjectPack.BACKGROUND_FILE, archive.backgroundBytes)
        AppBackground.importJson(context, archive.backgroundJson, bundledBackground)
        PadBankStore.importJson(context, archive.padsJson)
        PlaylistStore.importJson(context, archive.playlistJson)
        PluginParamStore.importJson(context, archive.pluginsJson)
        val layout = ModuleLayoutStore.importJson(context, archive.layoutJson)
        return ImportResult(layout = layout, jsonOnly = false)
    }

    data class ImportResult(val layout: MutableList<ModuleEntry>?, val jsonOnly: Boolean)

    private fun importLibrary(context: Context, archive: ProjectArchive) {
        val obj = try {
            JSONObject(archive.libraryJson)
        } catch (e: Exception) {
            return
        }
        val array = obj.optJSONArray("entries") ?: return
        val restored = mutableListOf<SampleEntry>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optInt("id", -1)
            val file = item.optString("file")
            val wavBytes = archive.samples[file] ?: continue
            if (id < 0) continue
            val saved = writeSample(context, id, wavBytes) ?: continue
            restored.add(
                SampleEntry(
                    uri = saved,
                    displayName = item.optString("name", "sample $id"),
                    category = item.optString("category", SoundCategory.DEFAULT),
                    id = id,
                    tags = item.optString("tags", ""),
                ),
            )
        }
        SampleLibrary(context).upsertAll(restored)
    }

    private fun sampleBytes(context: Context, entry: SampleEntry): ByteArray? {
        return try {
            val raw = context.contentResolver.openInputStream(entry.uri)?.use { it.readBytes() } ?: return null
            try {
                WavIO.read(raw)
                raw
            } catch (e: Exception) {
                val wav = SampleLoader.decode(context.contentResolver, entry.uri)
                val out = ByteArrayOutputStream()
                WavIO.write(wav, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun backgroundBytes(context: Context): ByteArray? {
        val uri = AppBackground.currentMediaUri(context) ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeSample(context: Context, id: Int, bytes: ByteArray): Uri? {
        return try {
            val dir = File(context.filesDir, "slices").apply { mkdirs() }
            val file = File(dir, "bundle-$id.wav")
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeBundledFile(context: Context, name: String, bytes: ByteArray?): Uri? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val dir = File(context.filesDir, "bundle").apply { mkdirs() }
            val file = File(dir, name.substringAfterLast('/').ifBlank { "background.bin" })
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}
