package com.sai.app

import android.content.Context
import com.sai.core.tracker.Song
import org.json.JSONArray
import org.json.JSONObject

/** Per-channel state for the FL Studio-style Channel Rack (mute, pan, volume, routing, sample). */
data class RackChannelState(
    val instrumentId: Int? = null,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val volume: Float = 0.78f,
    val pan: Float = 0.5f,
    val mixerTrack: Int = 0,
) {
    fun withInstrument(id: Int?) = copy(instrumentId = id)
    fun withMuted(value: Boolean) = copy(muted = value)
    fun withSoloed(value: Boolean) = copy(soloed = value)
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

    @Volatile private var memory: List<RackChannelState>? = null

    fun loadChannels(context: Context): MutableList<RackChannelState> {
        memory?.let { return it.toMutableList() }
        val raw = prefs(context).getString(KEY_CHANNELS, null)
        if (raw == null) {
            val defaults = defaultChannels(context)
            memory = defaults.toList()
            return defaults
        }
        return try {
            val array = JSONArray(raw)
            val loaded = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RackChannelState(
                    instrumentId = if (obj.has("instrument") && !obj.isNull("instrument")) obj.getInt("instrument") else null,
                    muted = obj.optBoolean("muted", false),
                    soloed = obj.optBoolean("soloed", false),
                    volume = obj.optDouble("volume", 0.78).toFloat(),
                    pan = obj.optDouble("pan", 0.5).toFloat(),
                    mixerTrack = obj.optInt("mixerTrack", 0),
                )
            }.toMutableList()
            memory = loaded.toList()
            loaded
        } catch (e: Exception) {
            val defaults = defaultChannels(context)
            memory = defaults.toList()
            defaults
        }
    }

    /** Live channel for a tracker track index, or null if that row isn't in the rack. */
    fun channel(context: Context, track: Int): RackChannelState? =
        loadChannels(context).getOrNull(track)

    fun visibleCount(context: Context): Int =
        prefs(context).getInt(KEY_VISIBLE_COUNT, MIN_VISIBLE).coerceIn(MIN_VISIBLE, MAX_CHANNELS)

    fun setVisibleCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_VISIBLE_COUNT, count.coerceIn(MIN_VISIBLE, MAX_CHANNELS)).apply()
    }

    fun saveChannels(context: Context, channels: List<RackChannelState>) {
        memory = channels.toList()
        val array = JSONArray()
        for (channel in channels) {
            array.put(
                JSONObject()
                    .put("instrument", channel.instrumentId ?: JSONObject.NULL)
                    .put("muted", channel.muted)
                    .put("soloed", channel.soloed)
                    .put("volume", channel.volume.toDouble())
                    .put("pan", channel.pan.toDouble())
                    .put("mixerTrack", channel.mixerTrack),
            )
        }
        prefs(context).edit().putString(KEY_CHANNELS, array.toString()).apply()
    }

    /**
     * Assigns [instrumentIds] onto rack channels: empty visible rows first, then newly shown
     * rows up to [MAX_CHANNELS]. Leftover ids are ignored. Returns how many were placed.
     */
    fun sendToRack(context: Context, instrumentIds: List<Int>): Int {
        if (instrumentIds.isEmpty()) return 0
        val channels = loadChannels(context)
        var visible = visibleCount(context)
        val targets = mutableListOf<Int>()
        for (i in 0 until visible) {
            if (channels.getOrNull(i)?.instrumentId == null) targets.add(i)
        }
        var next = visible
        while (targets.size < instrumentIds.size && next < MAX_CHANNELS) {
            targets.add(next)
            next++
        }
        var overwrite = 0
        while (targets.size < instrumentIds.size && overwrite < MAX_CHANNELS) {
            if (overwrite !in targets) targets.add(overwrite)
            overwrite++
        }
        val placed = minOf(instrumentIds.size, targets.size, MAX_CHANNELS)
        for (i in 0 until placed) {
            val index = targets[i]
            while (channels.size <= index) channels.add(RackChannelState())
            channels[index] = channels[index].withInstrument(instrumentIds[i])
            visible = maxOf(visible, index + 1)
        }
        setVisibleCount(context, visible.coerceIn(MIN_VISIBLE, MAX_CHANNELS))
        saveChannels(context, channels)
        return placed
    }

    fun anySolo(context: Context): Boolean = loadChannels(context).any { it.soloed }

    fun exportJson(context: Context): String {
        val array = JSONArray()
        for (channel in loadChannels(context)) {
            array.put(
                JSONObject()
                    .put("instrument", channel.instrumentId ?: JSONObject.NULL)
                    .put("muted", channel.muted)
                    .put("soloed", channel.soloed)
                    .put("volume", channel.volume.toDouble())
                    .put("pan", channel.pan.toDouble())
                    .put("mixerTrack", channel.mixerTrack),
            )
        }
        return JSONObject()
            .put("visibleCount", visibleCount(context))
            .put("channels", array)
            .toString()
    }

    fun importJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        try {
            val obj = JSONObject(raw)
            val array = obj.optJSONArray("channels") ?: JSONArray(raw)
            val loaded = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                RackChannelState(
                    instrumentId = if (item.has("instrument") && !item.isNull("instrument")) item.getInt("instrument") else null,
                    muted = item.optBoolean("muted", false),
                    soloed = item.optBoolean("soloed", false),
                    volume = item.optDouble("volume", 0.78).toFloat(),
                    pan = item.optDouble("pan", 0.5).toFloat(),
                    mixerTrack = item.optInt("mixerTrack", 0),
                )
            }
            if (obj.has("visibleCount")) setVisibleCount(context, obj.getInt("visibleCount"))
            saveChannels(context, loaded)
        } catch (e: Exception) {
            // Leave the current rack if the package chunk is malformed.
        }
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
