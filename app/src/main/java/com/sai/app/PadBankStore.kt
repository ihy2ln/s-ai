package com.sai.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sixteen-pad bank assigned to library sample IDs. */
object PadBankStore {
    const val PAD_COUNT = 16
    private const val PREFS_NAME = "pad_bank"
    private const val KEY_PADS = "pads"

    fun load(context: Context): MutableList<Int?> {
        val raw = prefs(context).getString(KEY_PADS, null) ?: return MutableList(PAD_COUNT) { null }
        return try {
            val array = JSONArray(raw)
            MutableList(PAD_COUNT) { i ->
                if (i < array.length() && !array.isNull(i)) array.getInt(i) else null
            }
        } catch (e: Exception) {
            MutableList(PAD_COUNT) { null }
        }
    }

    fun save(context: Context, ids: List<Int?>) {
        val array = JSONArray()
        for (i in 0 until PAD_COUNT) {
            array.put(ids.getOrNull(i) ?: JSONObject.NULL)
        }
        prefs(context).edit().putString(KEY_PADS, array.toString()).apply()
    }

    fun exportJson(context: Context): String {
        val array = JSONArray()
        for (id in load(context)) array.put(id ?: JSONObject.NULL)
        return JSONObject().put("pads", array).toString()
    }

    fun importJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        try {
            val obj = JSONObject(raw)
            val array = obj.optJSONArray("pads") ?: return
            val ids = MutableList(PAD_COUNT) { i ->
                if (i < array.length() && !array.isNull(i)) array.getInt(i) else null
            }
            save(context, ids)
        } catch (e: Exception) {
            // Keep the current pads if the package chunk is malformed.
        }
    }

    fun set(context: Context, index: Int, id: Int?) {
        val ids = load(context)
        if (index in ids.indices) {
            ids[index] = id
            save(context, ids)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
