package com.sai.app

import android.content.Context
import org.json.JSONObject

/** Per-plugin knob values for Home instrument/effect modules. */
object PluginParamStore {
    private const val PREFS_NAME = "plugin_params"

    fun load(context: Context, pluginId: String): Map<String, Double> {
        val raw = prefs(context).getString(pluginId, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, Double>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                out[key] = obj.optDouble(key, 0.0)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun save(context: Context, pluginId: String, params: Map<String, Double>) {
        val obj = JSONObject()
        for ((key, value) in params) obj.put(key, value)
        prefs(context).edit().putString(pluginId, obj.toString()).apply()
    }

    fun exportJson(context: Context): String {
        val obj = JSONObject()
        val all = prefs(context).all
        for ((key, value) in all) {
            if (value is String) obj.put(key, value)
        }
        return obj.toString()
    }

    fun importJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        try {
            val obj = JSONObject(raw)
            val editor = prefs(context).edit()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                editor.putString(key, obj.optString(key))
            }
            editor.apply()
        } catch (e: Exception) {
            // Keep current params if the package chunk is malformed.
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
