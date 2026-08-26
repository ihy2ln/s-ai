package com.sai.app

import android.content.Context

/** Persists Synth ADSR so live play and Apply share the same envelope. */
object SynthStore {
    private const val PREFS_NAME = "synth"
    private const val KEY_ATTACK = "attack"
    private const val KEY_DECAY = "decay"
    private const val KEY_SUSTAIN = "sustain"
    private const val KEY_RELEASE = "release"

    fun attack(context: Context): Float = prefs(context).getFloat(KEY_ATTACK, 0.005f)
    fun decay(context: Context): Float = prefs(context).getFloat(KEY_DECAY, 0.08f)
    fun sustain(context: Context): Float = prefs(context).getFloat(KEY_SUSTAIN, 0.85f)
    fun release(context: Context): Float = prefs(context).getFloat(KEY_RELEASE, 0.12f)

    fun setAttack(context: Context, value: Float) = prefs(context).edit().putFloat(KEY_ATTACK, value.coerceIn(0f, 2f)).apply()
    fun setDecay(context: Context, value: Float) = prefs(context).edit().putFloat(KEY_DECAY, value.coerceIn(0f, 2f)).apply()
    fun setSustain(context: Context, value: Float) = prefs(context).edit().putFloat(KEY_SUSTAIN, value.coerceIn(0f, 1f)).apply()
    fun setRelease(context: Context, value: Float) = prefs(context).edit().putFloat(KEY_RELEASE, value.coerceIn(0f, 2f)).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
