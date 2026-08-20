package com.sai.app

import android.content.Context
import com.sai.core.tracker.Song
import org.json.JSONArray
import org.json.JSONObject

/** Per-channel state for the FL Studio-style Channel Rack (mute, pan, volume, routing, sample). */
data class RackChannelState(
    val instrumentIndex: Int? = null,
    val muted: Boolean = false,
    val volume: Float = 0.78f,
    val pan: Float = 0.5f,
    val mixerTrack: Int = 0,
) {
    fun withInstrument(index: Int?) = copy(instrumentIndex = index)
    fun withMuted(value: Boolean) = copy(muted = value)
    fun withVolume(value: Float) = copy(volume = value.coerceIn(0f, 1f))
    fun withPan(value: Float) = copy(pan = value.coerceIn(0f, 1f))
    fun withMixerTrack(value: Int) = copy(mixerTrack = value.coerceIn(0, 99))
}

object ChannelRackStore {
    private const val PREFS_NAME = "channel_rack"
    private const val KEY_CHANNELS = "channels"
    private const val KEY_VISIBLE_COUNT = "visible_count"
    const val MAX_CHANNELS = Song.TRACK_COUNT
    const val MIN_VISIBLE = 4

    fun loadChannels(context: Context): MutableList<RackChannelState> {
        val raw = prefs(context).getString(KEY_CHANNELS, null) ?: return defaultChannels(context)
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RackChannelState(
                    instrumentIndex = if (obj.has("instrument")) obj.getInt("instrument") else null,
                    muted = obj.optBoolean("muted", false),
                    volume = obj.optDouble("volume", 0.78).toFloat(),
                    pan = obj.optDouble("pan", 0.5).toFloat(),
                    mixerTrack = obj.optInt("mixerTrack", 0),
                )
            }.toMutableList()
        } catch (e: Exception) {
            defaultChannels(context)
        }
    }

    fun visibleCount(context: Context): Int =
        prefs(context).getInt(KEY_VISIBLE_COUNT, MIN_VISIBLE).coerceIn(MIN_VISIBLE, MAX_CHANNELS)

    fun setVisibleCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_VISIBLE_COUNT, count.coerceIn(MIN_VISIBLE, MAX_CHANNELS)).apply()
    }

    fun saveChannels(context: Context, channels: List<RackChannelState>) {
        val array = JSONArray()
        for (channel in channels) {
            array.put(
                JSONObject()
                    .put("instrument", channel.instrumentIndex ?: JSONObject.NULL)
                    .put("muted", channel.muted)
                    .put("volume", channel.volume.toDouble())
                    .put("pan", channel.pan.toDouble())
                    .put("mixerTrack", channel.mixerTrack),
            )
        }
        prefs(context).edit().putString(KEY_CHANNELS, array.toString()).apply()
    }

    fun defaultChannels(context: Context): MutableList<RackChannelState> {
        val count = visibleCount(context)
        return MutableList(count) { RackChannelState() }
    }

    /** FL-style channel button colors; index 0 is the missing-sample red. */
    fun channelColor(index: Int, hasInstrument: Boolean): Int {
        if (!hasInstrument) return android.graphics.Color.rgb(170, 45, 45)
        val palette = intArrayOf(
            android.graphics.Color.rgb(70, 110, 165),
            android.graphics.Color.rgb(55, 130, 85),
            android.graphics.Color.rgb(150, 95, 55),
            android.graphics.Color.rgb(120, 70, 140),
            android.graphics.Color.rgb(50, 130, 130),
            android.graphics.Color.rgb(140, 120, 50),
            android.graphics.Color.rgb(90, 90, 150),
            android.graphics.Color.rgb(130, 80, 80),
        )
        return palette[index % palette.size]
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
