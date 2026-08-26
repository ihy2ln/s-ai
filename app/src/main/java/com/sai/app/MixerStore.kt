package com.sai.app

import android.content.Context
import com.sai.core.audio.InsertFx
import com.sai.core.audio.InsertKind
import com.sai.core.audio.InsertSlot
import com.sai.core.audio.MixerMath
import org.json.JSONArray
import org.json.JSONObject

data class MixerStripState(
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val volume: Float = 1f,
    val insert: InsertSlot = InsertSlot(),
) {
    fun withMuted(value: Boolean) = copy(muted = value)
    fun withSoloed(value: Boolean) = copy(soloed = value)
    fun withVolume(value: Float) = copy(volume = value.coerceIn(0f, 1f))
    fun withInsert(value: InsertSlot) = copy(insert = value)

    fun toMath() = MixerMath.Strip(muted = muted, soloed = soloed, volume = volume, insert = insert)
}

object MixerStore {
    private const val PREFS_NAME = "mixer"
    private const val KEY_STRIPS = "strips"
    private const val KEY_MASTER = "master"
    private const val KEY_MASTER_MUTED = "master_muted"
    private const val KEY_MASTER_INSERT = "master_insert"
    const val STRIP_COUNT = MixerMath.STRIP_COUNT

    @Volatile private var stripsMemory: List<MixerStripState>? = null
    @Volatile private var masterMemory: Float? = null
    @Volatile private var masterMutedMemory: Boolean? = null
    @Volatile private var masterInsertMemory: InsertSlot? = null

    private val peaks = FloatArray(STRIP_COUNT + 1)
    private val lock = Any()

    fun loadStrips(context: Context): MutableList<MixerStripState> {
        stripsMemory?.let { return it.toMutableList() }
        val raw = prefs(context).getString(KEY_STRIPS, null)
        if (raw == null) {
            val defaults = MutableList(STRIP_COUNT) { MixerStripState() }
            stripsMemory = defaults.toList()
            return defaults
        }
        return try {
            val array = JSONArray(raw)
            val loaded = (0 until STRIP_COUNT).map { i ->
                val obj = if (i < array.length()) array.getJSONObject(i) else JSONObject()
                MixerStripState(
                    muted = obj.optBoolean("muted", false),
                    soloed = obj.optBoolean("soloed", false),
                    volume = obj.optDouble("volume", 1.0).toFloat(),
                    insert = insertFromJson(obj.optJSONObject("insert")),
                )
            }.toMutableList()
            stripsMemory = loaded.toList()
            loaded
        } catch (e: Exception) {
            val defaults = MutableList(STRIP_COUNT) { MixerStripState() }
            stripsMemory = defaults.toList()
            defaults
        }
    }

    fun saveStrips(context: Context, strips: List<MixerStripState>) {
        stripsMemory = strips.toList()
        val array = JSONArray()
        for (strip in strips) {
            array.put(stripToJson(strip))
        }
        prefs(context).edit().putString(KEY_STRIPS, array.toString()).apply()
    }

    fun masterVolume(context: Context): Float {
        masterMemory?.let { return it }
        val value = prefs(context).getFloat(KEY_MASTER, 1f).coerceIn(0f, 1f)
        masterMemory = value
        return value
    }

    fun setMasterVolume(context: Context, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        masterMemory = clamped
        prefs(context).edit().putFloat(KEY_MASTER, clamped).apply()
    }

    fun masterMuted(context: Context): Boolean {
        masterMutedMemory?.let { return it }
        val value = prefs(context).getBoolean(KEY_MASTER_MUTED, false)
        masterMutedMemory = value
        return value
    }

    fun setMasterMuted(context: Context, muted: Boolean) {
        masterMutedMemory = muted
        prefs(context).edit().putBoolean(KEY_MASTER_MUTED, muted).apply()
    }

    fun masterInsert(context: Context): InsertSlot {
        masterInsertMemory?.let { return it }
        val raw = prefs(context).getString(KEY_MASTER_INSERT, null)
        val slot = if (raw.isNullOrBlank()) InsertSlot() else {
            try {
                insertFromJson(JSONObject(raw))
            } catch (e: Exception) {
                InsertSlot()
            }
        }
        masterInsertMemory = slot
        return slot
    }

    fun setMasterInsert(context: Context, slot: InsertSlot) {
        masterInsertMemory = slot
        prefs(context).edit().putString(KEY_MASTER_INSERT, insertToJson(slot).toString()).apply()
    }

    fun mathStrips(context: Context): List<MixerMath.Strip> = loadStrips(context).map { it.toMath() }

    fun exportJson(context: Context): String {
        val array = JSONArray()
        for (strip in loadStrips(context)) {
            array.put(stripToJson(strip))
        }
        return JSONObject()
            .put("strips", array)
            .put("master", masterVolume(context).toDouble())
            .put("masterMuted", masterMuted(context))
            .put("masterInsert", insertToJson(masterInsert(context)))
            .toString()
    }

    fun importJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        try {
            val obj = JSONObject(raw)
            val array = obj.optJSONArray("strips") ?: JSONArray()
            val loaded = (0 until STRIP_COUNT).map { i ->
                val item = if (i < array.length()) array.getJSONObject(i) else JSONObject()
                MixerStripState(
                    muted = item.optBoolean("muted", false),
                    soloed = item.optBoolean("soloed", false),
                    volume = item.optDouble("volume", 1.0).toFloat(),
                    insert = insertFromJson(item.optJSONObject("insert")),
                )
            }
            saveStrips(context, loaded)
            if (obj.has("master")) setMasterVolume(context, obj.optDouble("master", 1.0).toFloat())
            if (obj.has("masterMuted")) setMasterMuted(context, obj.optBoolean("masterMuted", false))
            if (obj.has("masterInsert")) {
                val insertObj = obj.optJSONObject("masterInsert")
                setMasterInsert(context, insertFromJson(insertObj))
            }
        } catch (e: Exception) {
            // Leave the current mixer if the package chunk is malformed.
        }
    }

    fun hit(stripIndex: Int?, level: Float) {
        val peak = level.coerceIn(0f, 1f)
        synchronized(lock) {
            if (stripIndex != null && stripIndex in peaks.indices) {
                peaks[stripIndex] = maxOf(peaks[stripIndex], peak)
            }
            peaks[STRIP_COUNT] = maxOf(peaks[STRIP_COUNT], peak)
        }
    }

    fun snapshotPeaks(): FloatArray = synchronized(lock) { peaks.copyOf() }

    fun decayPeaks(factor: Float = 0.82f) {
        synchronized(lock) {
            for (i in peaks.indices) peaks[i] *= factor
        }
    }

    private fun stripToJson(strip: MixerStripState) = JSONObject()
        .put("muted", strip.muted)
        .put("soloed", strip.soloed)
        .put("volume", strip.volume.toDouble())
        .put("insert", insertToJson(strip.insert))

    internal fun insertToJson(slot: InsertSlot): JSONObject {
        val params = JSONObject()
        for ((key, value) in slot.params) params.put(key, value)
        return JSONObject()
            .put("kind", slot.kind.name)
            .put("bypassed", slot.bypassed)
            .put("params", params)
    }

    internal fun insertFromJson(obj: JSONObject?): InsertSlot {
        if (obj == null) return InsertSlot()
        val kind = try {
            InsertKind.valueOf(obj.optString("kind", InsertKind.NONE.name))
        } catch (e: Exception) {
            InsertKind.NONE
        }
        val paramsObj = obj.optJSONObject("params") ?: JSONObject()
        val overlay = mutableMapOf<String, Double>()
        val keys = paramsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            overlay[key] = paramsObj.optDouble(key, 0.0)
        }
        return InsertSlot(
            kind = kind,
            bypassed = obj.optBoolean("bypassed", false),
            params = InsertFx.mergeDefaults(kind, overlay),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
