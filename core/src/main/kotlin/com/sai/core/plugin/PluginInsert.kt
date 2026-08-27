package com.sai.core.plugin

import com.sai.core.audio.InsertFx
import com.sai.core.audio.InsertKind
import com.sai.core.audio.InsertSlot

/** Build a mixer [InsertSlot] from a catalog record (aliases, one-knobs, default params). */
object PluginInsert {

    fun slotFor(
        plugin: PluginDescriptor,
        amount: Double = 0.45,
        overlay: Map<String, Double> = emptyMap(),
    ): InsertSlot? {
        OneKnobs.byId(plugin.id)?.let { return OneKnobs.slot(plugin.id, amount) }
        val kind = kindOf(plugin) ?: return null
        if (kind == InsertKind.NONE) return null
        return InsertSlot(
            kind = kind,
            params = InsertFx.mergeDefaults(kind, plugin.defaultParams + overlay),
            engineId = plugin.engineId.ifBlank { plugin.id },
        )
    }

    fun kindOf(plugin: PluginDescriptor): InsertKind? {
        val named = plugin.insertKind
        if (!named.isNullOrBlank()) {
            return try {
                InsertKind.valueOf(named)
            } catch (e: Exception) {
                InsertFx.kindForEngine(plugin.engineId.ifBlank { plugin.id })
            }
        }
        return InsertFx.kindForEngine(plugin.engineId.ifBlank { plugin.id }).takeIf { it != InsertKind.NONE }
    }

    fun displayName(slot: InsertSlot): String {
        if (slot.engineId.isNotBlank()) {
            PluginCatalog.byId(slot.engineId)?.let { return it.name }
            OneKnobs.byId(slot.engineId)?.let { return it.name }
        }
        return when (slot.kind) {
            InsertKind.NONE -> "Off"
            InsertKind.DISTORTION -> "Distort"
            InsertKind.EQUALIZER -> "Equalizer"
            InsertKind.STEREO -> "Stereo"
            else -> slot.kind.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
