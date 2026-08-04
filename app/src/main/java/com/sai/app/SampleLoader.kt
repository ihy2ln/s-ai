package com.sai.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO

object SampleLoader {

    fun decode(resolver: ContentResolver, uri: Uri): Wav {
        val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
        return try {
            WavIO.read(bytes)
        } catch (wavError: Exception) {
            AudioDecoder.decode(resolver, uri)
        }
    }

    fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "sample"
    }
}
