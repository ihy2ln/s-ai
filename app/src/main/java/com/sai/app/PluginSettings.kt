package com.sai.app

import android.content.Context

class PluginSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(pluginId: String, defaultValue: Boolean = true): Boolean =
        prefs.getBoolean(pluginId, defaultValue)

    fun setEnabled(pluginId: String, enabled: Boolean) {
        prefs.edit().putBoolean(pluginId, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "plugin_settings"
    }
}
