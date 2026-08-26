package com.sai.app

import android.content.Context

/** Playback buffer multiplier, record latency compensation, and vocal FX on tape takes. */
object LatencyStore {
    private const val PREFS_NAME = "latency"
    private const val KEY_BUFFER = "buffer_x"
    private const val KEY_OFFSET = "record_offset_ms"
    private const val KEY_VOCAL = "vocal_fx"

    fun bufferMultiplier(context: Context): Int =
        prefs(context).getInt(KEY_BUFFER, 1).coerceIn(1, 4)

    fun setBufferMultiplier(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_BUFFER, value.coerceIn(1, 4)).apply()
    }

    fun recordOffsetMs(context: Context): Int =
        prefs(context).getInt(KEY_OFFSET, 0).coerceIn(0, 200)

    fun setRecordOffsetMs(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_OFFSET, value.coerceIn(0, 200)).apply()
    }

    fun vocalFx(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOCAL, true)

    fun setVocalFx(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOCAL, on).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
