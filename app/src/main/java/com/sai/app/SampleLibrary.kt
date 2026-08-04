package com.sai.app

import android.content.Context
import android.net.Uri

data class SampleEntry(val uri: Uri, val displayName: String)

class SampleLibrary(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<SampleEntry> =
        prefs.getStringSet(KEY_ENTRIES, emptySet())
            .orEmpty()
            .mapNotNull(::decode)
            .sortedBy { it.displayName.lowercase() }

    fun add(entries: List<SampleEntry>) {
        val existing = prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toMutableSet()
        entries.forEach { existing.add(encode(it)) }
        prefs.edit().putStringSet(KEY_ENTRIES, existing).apply()
    }

    private fun encode(entry: SampleEntry): String = "${entry.uri}|${entry.displayName}"

    private fun decode(raw: String): SampleEntry? {
        val separatorIndex = raw.indexOf('|')
        if (separatorIndex < 0) return null
        return SampleEntry(
            uri = Uri.parse(raw.substring(0, separatorIndex)),
            displayName = raw.substring(separatorIndex + 1),
        )
    }

    companion object {
        private const val PREFS_NAME = "sample_library"
        private const val KEY_ENTRIES = "entries"
    }
}
