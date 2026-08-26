package com.sai.app

import android.content.Context

/** Live transport extras that are not part of the project file: metronome and count-in. */
object TransportStore {
    private const val PREFS_NAME = "transport"
    private const val KEY_METRONOME = "metronome"
    private const val KEY_COUNT_IN = "count_in"

    fun metronome(context: Context): Boolean =
        prefs(context).getBoolean(KEY_METRONOME, false)

    fun setMetronome(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_METRONOME, on).apply()
    }

    fun countIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COUNT_IN, false)

    fun setCountIn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_COUNT_IN, on).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
