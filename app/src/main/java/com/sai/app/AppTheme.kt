package com.sai.app

import android.content.Context
import android.graphics.Color

/** Persists the app-wide accent color (titles, knobs, lit step cells) and window/button opacity. */
object AppTheme {
    private const val PREFS_NAME = "app_theme"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_OPACITY = "opacity_percent"
    private const val KEY_BACKGROUND_OPACITY = "background_opacity_percent"

    private const val DEFAULT_ACCENT = Color.CYAN
    private const val DEFAULT_OPACITY = 60
    private const val DEFAULT_BACKGROUND_OPACITY = 100

    fun accentColor(context: Context): Int = prefs(context).getInt(KEY_ACCENT, DEFAULT_ACCENT)

    fun setAccentColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, color).apply()
    }

    /** 0..100, applied as background alpha to windows/pill buttons. */
    fun opacityPercent(context: Context): Int = prefs(context).getInt(KEY_OPACITY, DEFAULT_OPACITY)

    fun setOpacityPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_OPACITY, percent.coerceIn(10, 100)).apply()
    }

    fun opacityAlpha(context: Context): Int = (opacityPercent(context) * 255 / 100).coerceIn(0, 255)

    /** 0..100: how much of the dark base scrim covers a picture/video background.
     *  100 = fully opaque (picture/video hidden, today's default look), 0 = picture/video fully visible.
     *  Lowering this never removes the chosen background - it only reveals more of it. */
    fun backgroundOpacityPercent(context: Context): Int = prefs(context).getInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY)

    fun setBackgroundOpacityPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_BACKGROUND_OPACITY, percent.coerceIn(0, 100)).apply()
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit()
            .putInt(KEY_ACCENT, DEFAULT_ACCENT)
            .putInt(KEY_OPACITY, DEFAULT_OPACITY)
            .putInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY)
            .apply()
    }

    fun exportJson(context: Context): String =
        org.json.JSONObject()
            .put("accent", accentColor(context))
            .put("opacity", opacityPercent(context))
            .put("backgroundOpacity", backgroundOpacityPercent(context))
            .toString()

    fun importJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        try {
            val obj = org.json.JSONObject(raw)
            if (obj.has("accent")) setAccentColor(context, obj.getInt("accent"))
            if (obj.has("opacity")) setOpacityPercent(context, obj.getInt("opacity"))
            if (obj.has("backgroundOpacity")) setBackgroundOpacityPercent(context, obj.getInt("backgroundOpacity"))
        } catch (e: Exception) {
            // Keep the current theme if the package chunk is malformed.
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
