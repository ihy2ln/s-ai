package com.sai.app

import android.content.Context
import com.sai.core.stem.StemBackend
import com.sai.core.stem.StemSplitMode
import com.sai.core.stem.StemSplitSettings

class StemSplitterSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): StemSplitSettings = StemSplitSettings(
        backend = StemBackend.entries.getOrElse(prefs.getInt(KEY_BACKEND, 0)) { StemBackend.COMFY_UI },
        comfyBaseUrl = prefs.getString(KEY_COMFY_URL, "").orEmpty(),
        comfyApiKey = prefs.getString(KEY_COMFY_API_KEY, "").orEmpty(),
        demucsModel = prefs.getString(KEY_DEMUCS_MODEL, "htdemucs").orEmpty().ifBlank { "htdemucs" },
        pollIntervalMs = prefs.getLong(KEY_POLL_MS, 1_500L).coerceAtLeast(500L),
        requestTimeoutMs = prefs.getInt(KEY_TIMEOUT_MS, 30_000).coerceAtLeast(5_000),
        maxWaitMs = prefs.getLong(KEY_MAX_WAIT_MS, 20 * 60_000L).coerceAtLeast(60_000L),
    )

    fun save(settings: StemSplitSettings) {
        prefs.edit()
            .putInt(KEY_BACKEND, settings.backend.ordinal)
            .putString(KEY_COMFY_URL, settings.comfyBaseUrl.trim())
            .putString(KEY_COMFY_API_KEY, settings.comfyApiKey.trim())
            .putString(KEY_DEMUCS_MODEL, settings.demucsModel.trim().ifBlank { "htdemucs" })
            .putLong(KEY_POLL_MS, settings.pollIntervalMs)
            .putInt(KEY_TIMEOUT_MS, settings.requestTimeoutMs)
            .putLong(KEY_MAX_WAIT_MS, settings.maxWaitMs)
            .apply()
    }

    fun defaultMode(): StemSplitMode =
        StemSplitMode.entries.getOrElse(prefs.getInt(KEY_DEFAULT_MODE, 0)) { StemSplitMode.FOUR_STEM }

    fun setDefaultMode(mode: StemSplitMode) {
        prefs.edit().putInt(KEY_DEFAULT_MODE, mode.ordinal).apply()
    }

    fun sendToRackAfterSplit(): Boolean = prefs.getBoolean(KEY_TO_RACK, false)

    fun setSendToRackAfterSplit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TO_RACK, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "stem_splitter"
        private const val KEY_BACKEND = "backend"
        private const val KEY_COMFY_URL = "comfy_url"
        private const val KEY_COMFY_API_KEY = "comfy_api_key"
        private const val KEY_DEMUCS_MODEL = "demucs_model"
        private const val KEY_POLL_MS = "poll_ms"
        private const val KEY_TIMEOUT_MS = "timeout_ms"
        private const val KEY_MAX_WAIT_MS = "max_wait_ms"
        private const val KEY_DEFAULT_MODE = "default_mode"
        private const val KEY_TO_RACK = "to_rack"
    }
}
