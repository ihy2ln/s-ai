package com.sai.app

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Persisted accent/opacity plus the FL Mobile design tokens every screen shares.
 * Navy/charcoal canvas, sparse teal accent, gold for playhead/key stats.
 */
object AppTheme {
    private const val PREFS_NAME = "app_theme"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_OPACITY = "opacity_percent"
    private const val KEY_BACKGROUND_OPACITY = "background_opacity_percent"

    /** Premium teal — used sparingly for titles, knobs, selected chips, CTAs. */
    private val DEFAULT_ACCENT = Color.rgb(46, 217, 166)
    private const val DEFAULT_OPACITY = 78
    private const val DEFAULT_BACKGROUND_OPACITY = 100

    /** Canvas behind modules — navy charcoal, not flat black. */
    val canvas: Int = Color.rgb(15, 20, 28)
    val surface: Int = Color.rgb(24, 33, 45)
    val surfaceRaised: Int = Color.rgb(30, 40, 54)
    val surfaceMuted: Int = Color.rgb(18, 25, 36)
    val border: Int = Color.rgb(42, 54, 72)
    val header: Int = Color.rgb(22, 30, 42)

    val textPrimary: Int = Color.rgb(240, 243, 247)
    val textSecondary: Int = Color.rgb(139, 151, 168)
    val textMuted: Int = Color.rgb(92, 107, 124)

    val gold: Int = Color.rgb(232, 197, 71)
    val play: Int = Color.rgb(61, 220, 132)
    val record: Int = Color.rgb(224, 58, 58)
    val danger: Int = Color.rgb(224, 69, 69)
    val info: Int = Color.rgb(91, 163, 232)

    val loopTint: Int = Color.rgb(22, 42, 38)
    val playheadRow: Int = Color.rgb(10, 54, 60)
    val pianoWhite: Int = Color.rgb(22, 28, 38)
    val pianoBlack: Int = Color.rgb(16, 21, 30)

    const val TYPE_TITLE = 18f
    const val TYPE_SCREEN = 16f
    const val TYPE_BODY = 14f
    const val TYPE_META = 12f
    const val TYPE_CHIP = 11f
    const val TYPE_MICRO = 10f

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

    /**
     * 0..100: how much of the dark base scrim covers a picture/video background.
     * 100 = fully opaque (picture/video hidden, today's default look), 0 = picture/video fully visible.
     * Lowering this never removes the chosen background - it only reveals more of it.
     */
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

    fun withAlpha(color: Int, alpha: Int): Int = ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255))

    fun accentSoft(context: Context, alpha: Int = 0x4D): Int = withAlpha(accentColor(context), alpha)

    fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
