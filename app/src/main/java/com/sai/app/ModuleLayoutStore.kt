package com.sai.app

import android.content.Context

enum class ModuleType(val label: String) {
    SAMPLER("SAMPLER"), SYNTH("SYNTH"), TRACKER("TRACKER"), STEP_SEQUENCER("CHANNEL RACK"),
}

data class ModuleEntry(val type: ModuleType, var heightDp: Float)

/** Persists which modules are on the Home screen, in what order, how tall each one is, and each
 *  one's Cut Itself (choke/monophonic) toggle. */
object ModuleLayoutStore {
    private const val PREFS_NAME = "module_layout"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_CHOKE_PREFIX = "choke_"

    private val DEFAULT_HEIGHTS = mapOf(
        ModuleType.SAMPLER to 260f,
        ModuleType.SYNTH to 260f,
        ModuleType.TRACKER to 280f,
        ModuleType.STEP_SEQUENCER to 360f,
    )

    fun defaultHeight(type: ModuleType): Float = DEFAULT_HEIGHTS[type] ?: 240f

    /** Tracker and Step Sequencer edit/play the same song data through one shared playback
     *  engine, so they share a single Cut Itself value even though each shows its own toggle. */
    fun chokeKey(type: ModuleType): ModuleType = if (type == ModuleType.STEP_SEQUENCER) ModuleType.TRACKER else type

    fun isChokeEnabled(context: Context, type: ModuleType): Boolean =
        prefs(context).getBoolean(KEY_CHOKE_PREFIX + chokeKey(type).name, false)

    fun setChokeEnabled(context: Context, type: ModuleType, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHOKE_PREFIX + chokeKey(type).name, enabled).apply()
    }

    fun load(context: Context): MutableList<ModuleEntry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null)
        if (raw.isNullOrBlank()) {
            return ModuleType.values().map { ModuleEntry(it, defaultHeight(it)) }.toMutableList()
        }
        val loaded = raw.split("|").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size != 2) return@mapNotNull null
            val type = try { ModuleType.valueOf(parts[0]) } catch (e: IllegalArgumentException) { return@mapNotNull null }
            ModuleEntry(type, parts[1].toFloatOrNull() ?: defaultHeight(type))
        }
        return if (loaded.isEmpty()) mutableListOf() else loaded.toMutableList()
    }

    fun save(context: Context, entries: List<ModuleEntry>) {
        val raw = entries.joinToString("|") { "${it.type.name}:${it.heightDp}" }
        prefs(context).edit().putString(KEY_ENTRIES, raw).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
