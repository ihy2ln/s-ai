package com.sai.app

import android.content.Context
import com.sai.core.tracker.ClipKind
import com.sai.core.tracker.PlaylistClip
import org.json.JSONArray
import org.json.JSONObject

object PlaylistStore {
    private const val PREFS_NAME = "playlist"
    private const val KEY_CLIPS = "clips"

    fun load(context: Context): MutableList<PlaylistClip> {
        val raw = prefs(context).getString(KEY_CLIPS, null) ?: return mutableListOf()
        return decode(raw).toMutableList()
    }

    fun save(context: Context, clips: List<PlaylistClip>) {
        prefs(context).edit().putString(KEY_CLIPS, encode(clips)).apply()
    }

    fun exportJson(context: Context): String = JSONObject().put("clips", JSONArray(encodeList(load(context)))).toString()

    fun importJson(context: Context, raw: String) {
        if (raw.isBlank()) return
        try {
            val obj = JSONObject(raw)
            val array = obj.optJSONArray("clips") ?: return
            save(context, decodeArray(array))
        } catch (e: Exception) {
            // Keep the current playlist if the package chunk is malformed.
        }
    }

    fun add(context: Context, clip: PlaylistClip) {
        val clips = load(context)
        clips.add(clip)
        save(context, clips)
    }

    fun update(context: Context, clip: PlaylistClip) {
        val clips = load(context)
        val index = clips.indexOfFirst { it.id == clip.id }
        if (index >= 0) {
            clips[index] = clip
            save(context, clips)
        }
    }

    fun remove(context: Context, id: Int) {
        save(context, load(context).filter { it.id != id })
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun encode(clips: List<PlaylistClip>): String = JSONArray(encodeList(clips)).toString()

    private fun encodeList(clips: List<PlaylistClip>): List<JSONObject> =
        clips.map { clip ->
            JSONObject()
                .put("id", clip.id)
                .put("kind", clip.kind.name)
                .put("lane", clip.lane)
                .put("startStep", clip.startStep)
                .put("lengthSteps", clip.lengthSteps)
                .put("muted", clip.muted)
                .apply {
                    clip.pattern?.let { put("pattern", it) }
                    clip.sampleId?.let { put("sampleId", it) }
                }
        }

    private fun decode(raw: String): List<PlaylistClip> {
        return try {
            decodeArray(JSONArray(raw))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeArray(array: JSONArray): List<PlaylistClip> {
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val kind = try {
                ClipKind.valueOf(obj.optString("kind", ClipKind.PATTERN.name))
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            PlaylistClip(
                id = obj.optInt("id", i + 1),
                kind = kind,
                lane = obj.optInt("lane", 0),
                startStep = obj.optInt("startStep", 0).coerceAtLeast(0),
                lengthSteps = obj.optInt("lengthSteps", 16).coerceAtLeast(1),
                pattern = if (obj.has("pattern") && !obj.isNull("pattern")) obj.getInt("pattern") else null,
                sampleId = if (obj.has("sampleId") && !obj.isNull("sampleId")) obj.getInt("sampleId") else null,
                muted = obj.optBoolean("muted", false),
            )
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
