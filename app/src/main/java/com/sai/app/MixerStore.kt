package com.sai.app

import android.content.Context
import com.sai.core.audio.InsertChain
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
    val chain: InsertChain = InsertChain.from(insert),
) {
    fun withMuted(value: Boolean) = copy(muted = value)
    fun withSoloed(value: Boolean) = copy(soloed = value)
    fun withVolume(value: Float) = copy(volume = value.coerceIn(0f, 1f))
    fun withInsert(value: InsertSlot) = copy(insert = value, chain = InsertChain.from(value))
    fun withChain(value: InsertChain) = copy(chain = value, insert = value.primary())

    fun toMath() = MixerMath.Strip(
        muted = muted,
        soloed = soloed,
        volume = volume,
        insert = insert,
        chain = if (chain.slots.isNotEmpty()) chain else InsertChain.from(insert),
    )
}

object MixerStore {
    private const val PREFS_NAME = "mixer"
    private const val KEY_STRIPS = "strips"
    private const val KEY_MASTER = "master"
    private const val KEY_MASTER_MUTED = "master_muted"
    private const val KEY_MASTER_INSERT = "master_insert"
    private const val KEY_MASTER_CHAIN = "master_chain"
    const val STRIP_COUNT = MixerMath.STRIP_COUNT

    @Volatile private var stripsMemory: List<MixerStripState>? = null
    @Volatile private var masterMemory: Float? = null
    @Volatile private var masterMutedMemory: Boolean? = null
    @Volatile private var masterInsertMemory: InsertSlot? = null
    @Volatile private var masterChainMemory: InsertChain? = null

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
                    chain = chainFromJson(obj.optJSONArray("chain"), obj.optJSONObject("insert")),
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

    fun masterInsert(context: Context): InsertSlot = masterChain(context).primary()

    fun setMasterInsert(context: Context, slot: InsertSlot) {
        setMasterChain(context, InsertChain.from(slot))
    }

    fun masterChain(context: Context): InsertChain {
        masterChainMemory?.let { return it }
        val raw = prefs(context).getString(KEY_MASTER_CHAIN, null)
        val chain = if (!raw.isNullOrBlank()) {
            try {
                chainFromJson(JSONArray(raw), null)
            } catch (e: Exception) {
                InsertChain()
            }
        } else {
            InsertChain.from(run {
                val legacy = prefs(context).getString(KEY_MASTER_INSERT, null)
                if (legacy.isNullOrBlank()) InsertSlot() else try {
                    insertFromJson(JSONObject(legacy))
                } catch (e: Exception) {
                    InsertSlot()
                }
            })
        }
        masterChainMemory = chain
        masterInsertMemory = chain.primary()
        return chain
    }

    fun setMasterChain(context: Context, chain: InsertChain) {
        masterChainMemory = chain
        masterInsertMemory = chain.primary()
        prefs(context).edit()
            .putString(KEY_MASTER_CHAIN, chainToJson(chain).toString())
            .putString(KEY_MASTER_INSERT, insertToJson(chain.primary()).toString())
            .apply()
    }

    fun appendInsert(context: Context, stripIndex: Int?, slot: InsertSlot) {
        if (slot.kind == InsertKind.NONE) return
        if (stripIndex == null) {
            setMasterChain(context, masterChain(context).plus(slot))
            return
        }
        val strips = loadStrips(context)
        if (stripIndex !in strips.indices) return
        val next = strips[stripIndex].chain.plus(slot)
        strips[stripIndex] = strips[stripIndex].withChain(next)
        saveStrips(context, strips)
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
            .put("masterChain", chainToJson(masterChain(context)))
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
                    chain = chainFromJson(item.optJSONArray("chain"), item.optJSONObject("insert")),
                )
            }
            saveStrips(context, loaded)
            if (obj.has("master")) setMasterVolume(context, obj.optDouble("master", 1.0).toFloat())
            if (obj.has("masterMuted")) setMasterMuted(context, obj.optBoolean("masterMuted", false))
            if (obj.has("masterChain")) {
                setMasterChain(context, chainFromJson(obj.optJSONArray("masterChain"), obj.optJSONObject("masterInsert")))
            } else if (obj.has("masterInsert")) {
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
        .put("insert", insertToJson(strip.chain.primary()))
        .put("chain", chainToJson(strip.chain))

    internal fun insertToJson(slot: InsertSlot): JSONObject {
        val params = JSONObject()
        for ((key, value) in slot.params) params.put(key, value)
        return JSONObject()
            .put("kind", slot.kind.name)
            .put("bypassed", slot.bypassed)
            .put("engineId", slot.engineId)
            .put("params", params)
    }

    internal fun insertFromJson(obj: JSONObject?): InsertSlot {
        if (obj == null) return InsertSlot()
        val kind = try {
            InsertKind.valueOf(obj.optString("kind", InsertKind.NONE.name))
        } catch (e: Exception) {
            InsertFx.kindForEngine(obj.optString("engineId", ""))
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
            engineId = obj.optString("engineId", ""),
        )
    }

    internal fun chainToJson(chain: InsertChain): JSONArray {
        val array = JSONArray()
        for (slot in chain.slots) array.put(insertToJson(slot))
        return array
    }

    internal fun chainFromJson(array: JSONArray?, legacyInsert: JSONObject?): InsertChain {
        if (array != null && array.length() > 0) {
            val slots = (0 until array.length()).map { insertFromJson(array.optJSONObject(it)) }
            return InsertChain(slots.filter { it.kind != InsertKind.NONE })
        }
        return InsertChain.from(insertFromJson(legacyInsert))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
