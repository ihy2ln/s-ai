package com.sai.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import java.io.File

object SliceExporter {

    fun saveToLibrary(context: Context, sourceName: String, slices: List<Wav>, category: String = SoundCategory.DEFAULT): List<SampleEntry> {
        val dir = slicesDir(context)
        val saved = slices.mapIndexed { index, slice ->
            val file = File(dir, "$sourceName-slice-${"%02d".format(index)}-${System.currentTimeMillis()}.wav")
            WavIO.write(slice, file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            SampleEntry(uri, "$sourceName #${"%02X".format(index)}", category)
        }
        return SampleLibrary(context).add(saved)
    }

    /** Overwrites a library sound in place (same stable id / name / category, new audio). */
    fun replaceLibraryEntry(context: Context, entry: SampleEntry, wav: Wav): SampleEntry {
        val dir = slicesDir(context)
        val file = File(dir, "id-${entry.id}-${System.currentTimeMillis()}.wav")
        WavIO.write(wav, file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val updated = entry.copy(uri = uri)
        SampleLibrary(context).replace(updated)
        deleteOwnedSlice(context, entry.uri, keep = file)
        return updated
    }

    private fun slicesDir(context: Context): File = File(context.filesDir, "slices").apply { mkdirs() }

    private fun deleteOwnedSlice(context: Context, uri: Uri, keep: File) {
        val dir = slicesDir(context)
        val name = uri.lastPathSegment ?: return
        val file = File(dir, name)
        try {
            if (file.exists() &&
                file.canonicalFile != keep.canonicalFile &&
                file.canonicalFile.startsWith(dir.canonicalFile)
            ) {
                file.delete()
            }
        } catch (e: Exception) {
            // Leave the previous file if we can't prove it's ours.
        }
    }
}
