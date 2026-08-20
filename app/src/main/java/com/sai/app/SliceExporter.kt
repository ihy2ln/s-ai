package com.sai.app

import android.content.Context
import androidx.core.content.FileProvider
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import java.io.File

object SliceExporter {

    fun saveToLibrary(context: Context, sourceName: String, slices: List<Wav>, category: String = SoundCategory.DEFAULT): List<SampleEntry> {
        val dir = File(context.filesDir, "slices").apply { mkdirs() }
        val saved = slices.mapIndexed { index, slice ->
            val file = File(dir, "$sourceName-slice-${"%02d".format(index)}-${System.currentTimeMillis()}.wav")
            WavIO.write(slice, file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            SampleEntry(uri, "$sourceName #${"%02X".format(index)}", category)
        }
        SampleLibrary(context).add(saved)
        return saved
    }
}
