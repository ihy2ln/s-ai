package com.sai.app

import android.content.Context
import android.net.Uri
import com.sai.core.project.StableIds
import org.json.JSONObject

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
    val id: Int = StableIds.UNASSIGNED,
)

class SampleLibrary(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<SampleEntry> = loadMigrated().sortedBy { it.displayName.lowercase() }

    fun get(id: Int): SampleEntry? = loadMigrated().find { it.id == id }

    fun byId(): Map<Int, SampleEntry> = loadMigrated().associateBy { it.id }

    fun byCategory(category: String): List<SampleEntry> = all().filter { it.category == category }

    fun add(entries: List<SampleEntry>): List<SampleEntry> {
        if (entries.isEmpty()) return emptyList()
        val current = loadMigrated().toMutableList()
        var next = nextId(current)
        val incoming = entries.map { entry ->
            if (entry.id >= 0 && current.none { it.id == entry.id }) {
                entry
            } else {
                entry.copy(id = next++)
            }
        }
        persist(current + incoming, next)
        return incoming
    }

    fun replace(entry: SampleEntry) {
        val current = loadMigrated().toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index < 0) {
            add(listOf(entry))
            return
        }
        current[index] = entry
        persist(current, nextId(current))
    }

    /** Inserts or replaces by stable id so a project package can restore phrase instrument IDs. */
    fun upsertAll(entries: List<SampleEntry>) {
        if (entries.isEmpty()) return
        val current = loadMigrated().toMutableList()
        for (entry in entries) {
            val index = current.indexOfFirst { it.id == entry.id }
            if (index >= 0) current[index] = entry else current.add(entry)
        }
        persist(current, nextId(current))
    }

    fun setCategory(entry: SampleEntry, category: String) {
        replace(entry.copy(category = category))
    }

    private fun loadMigrated(): List<SampleEntry> {
        val decoded = loadRaw()
        if (decoded.isEmpty()) return emptyList()
        if (decoded.all { it.id >= 0 }) return decoded
        val ordered = decoded.sortedBy { it.displayName.lowercase() }
        val assignment = StableIds.assign(ordered.map { if (it.id >= 0) it.id else null }, nextId(ordered))
        val migrated = ordered.mapIndexed { i, entry -> entry.copy(id = assignment.ids[i]) }
        persist(migrated, assignment.nextId)
        return migrated
    }

    private fun loadRaw(): List<SampleEntry> =
        prefs.getStringSet(KEY_ENTRIES, emptySet()).orEmpty().mapNotNull(::decode)

    private fun nextId(entries: List<SampleEntry>): Int {
        val stored = prefs.getInt(KEY_NEXT_ID, 0)
        val maxExisting = entries.map { it.id }.filter { it >= 0 }.maxOrNull() ?: -1
        return maxOf(stored, maxExisting + 1)
    }

    private fun persist(entries: List<SampleEntry>, nextId: Int) {
        prefs.edit()
            .putStringSet(KEY_ENTRIES, entries.map(::encode).toSet())
            .putInt(KEY_NEXT_ID, nextId)
            .apply()
    }

    private fun encode(entry: SampleEntry): String =
        JSONObject()
            .put("id", entry.id)
            .put("uri", entry.uri.toString())
            .put("name", entry.displayName)
            .put("category", entry.category)
            .toString()

    private fun decode(raw: String): SampleEntry? {
        if (raw.startsWith("{")) {
            return try {
                val obj = JSONObject(raw)
                SampleEntry(
                    uri = Uri.parse(obj.getString("uri")),
                    displayName = obj.getString("name"),
                    category = obj.optString("category", SoundCategory.DEFAULT),
                    id = obj.optInt("id", StableIds.UNASSIGNED),
                )
            } catch (e: Exception) {
                null
            }
        }
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
        private const val KEY_NEXT_ID = "next_id"
    }
}
