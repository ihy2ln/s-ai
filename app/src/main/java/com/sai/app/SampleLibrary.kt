package com.sai.app

import android.content.Context
import android.net.Uri

object SoundCategory {
    const val KICKS = "Kicks"
    const val SNARES = "Snares"
    const val HATS = "Hats"
    const val PERCUSSION = "Percussion"
    const val VOCALS = "Vocals"
    const val SFX = "SFX"
    const val SYNTH = "Synth"
    const val SAMPLES = "Samples"
    const val DEFAULT = SAMPLES

    val ALL = listOf(KICKS, SNARES, HATS, PERCUSSION, VOCALS, SFX, SYNTH, SAMPLES)
}

data class SampleEntry(
    val uri: Uri,
    val displayName: String,
    val category: String = SoundCategory.DEFAULT,
)

class SampleLibrary(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<SampleEntry> =
        prefs.getStringSet(KEY_ENTRIES, emptySet())
            .orEmpty()
            .mapNotNull(::decode)
            .sortedBy { it.displayName.lowercase() }

    fun byCategory(category: String): List<SampleEntry> = all().filter { it.category == category }

    fun add(entries: List<SampleEntry>) {
        val existing = prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toMutableSet()
        entries.forEach { existing.add(encode(it)) }
        prefs.edit().putStringSet(KEY_ENTRIES, existing).apply()
    }

    fun setCategory(entry: SampleEntry, category: String) {
        val existing = prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().toMutableSet()
        existing.remove(encode(entry))
        existing.add(encode(entry.copy(category = category)))
        prefs.edit().putStringSet(KEY_ENTRIES, existing).apply()
    }

    private fun encode(entry: SampleEntry): String = "${entry.uri}|${entry.displayName}|${entry.category}"

    private fun decode(raw: String): SampleEntry? {
        val firstSep = raw.indexOf('|')
        if (firstSep < 0) return null
        val uri = Uri.parse(raw.substring(0, firstSep))
        val lastSep = raw.lastIndexOf('|')
        return if (lastSep > firstSep) {
            SampleEntry(uri, raw.substring(firstSep + 1, lastSep), raw.substring(lastSep + 1))
        } else {
            SampleEntry(uri, raw.substring(firstSep + 1), SoundCategory.DEFAULT)
        }
    }

    companion object {
        private const val PREFS_NAME = "sample_library"
        private const val KEY_ENTRIES = "entries"
    }
}
