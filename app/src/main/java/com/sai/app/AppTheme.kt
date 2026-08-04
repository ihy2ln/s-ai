package com.sai.app

import android.content.Context
import android.graphics.Color

/** Persists the app-wide accent color (titles, knobs, lit step cells) and window/button opacity. */
object AppTheme {
    private const val PREFS_NAME = "app_theme"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_OPACITY = "opacity_percent"

    private const val DEFAULT_ACCENT = Color.CYAN
    private const val DEFAULT_OPACITY = 60

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

    fun resetToDefault(context: Context) {
        prefs(context).edit().putInt(KEY_ACCENT, DEFAULT_ACCENT).putInt(KEY_OPACITY, DEFAULT_OPACITY).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
