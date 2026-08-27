package com.sai.app

import android.content.Context
import com.sai.core.layout.ModuleBoxFrame
import com.sai.core.layout.WorkspaceLayout

enum class ModuleType(val label: String) {
    SAMPLER("SAMPLER"),
    SYNTH("SYNTH"),
    PADS("PADS"),
    TRACKER("TRACKER"),
    STEP_SEQUENCER("CHANNEL RACK"),
    PULSE_KEYS("PULSE KEYS"),
    SAW_LEAD("SAW LEAD"),
    SUB_BASS("SUB BASS"),
    PLUCK("PLUCK"),
    WARM_PAD("WARM PAD"),
    CLICK_KIT("CLICK KIT"),
    DELAY("DELAY"),
    DISTORT("DISTORT"),
    CHORUS("CHORUS"),
    LIMITER("LIMITER"),
    ;

    val isBuiltIn: Boolean
        get() = this == SAMPLER || this == SYNTH || this == PADS || this == TRACKER || this == STEP_SEQUENCER

    val isInstrumentPlugin: Boolean
        get() = this == PULSE_KEYS || this == SAW_LEAD || this == SUB_BASS ||
            this == PLUCK || this == WARM_PAD || this == CLICK_KIT

    val isEffectPlugin: Boolean
        get() = this == DELAY || this == DISTORT || this == CHORUS || this == LIMITER
}

data class ModuleEntry(val type: ModuleType, var heightDp: Float)

/** Persists which modules are on the Home screen, in what order, how tall each one is, and each
 *  one's Cut Itself (choke/monophonic) toggle. */
object ModuleLayoutStore {
    private const val PREFS_NAME = "module_layout"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_CHOKE_PREFIX = "choke_"
    private const val KEY_WORKSPACE = "workspace"
    private const val KEY_FOCUSED = "focused"
    private const val KEY_BOXES = "boxes"

    private val DEFAULT_HEIGHTS = mapOf(
        ModuleType.SAMPLER to 260f,
        ModuleType.SYNTH to 260f,
        ModuleType.PADS to 280f,
        ModuleType.TRACKER to 280f,
        ModuleType.STEP_SEQUENCER to 360f,
        ModuleType.PULSE_KEYS to 240f,
        ModuleType.SAW_LEAD to 240f,
        ModuleType.SUB_BASS to 240f,
        ModuleType.PLUCK to 240f,
        ModuleType.WARM_PAD to 240f,
        ModuleType.CLICK_KIT to 240f,
        ModuleType.DELAY to 220f,
        ModuleType.DISTORT to 220f,
        ModuleType.CHORUS to 220f,
        ModuleType.LIMITER to 220f,
    )

    fun defaultHeight(type: ModuleType): Float = DEFAULT_HEIGHTS[type] ?: 240f

    fun defaultEntries(): List<ModuleEntry> =
        listOf(ModuleType.SAMPLER, ModuleType.SYNTH, ModuleType.TRACKER, ModuleType.STEP_SEQUENCER)
            .map { ModuleEntry(it, defaultHeight(it)) }

    /** Restore factory module order and heights, clear box frames, and focus the first module.
     *  Keeps the current workspace mode (Stack / Focus / Boxes) and choke settings. */
    fun resetLayout(context: Context) {
        val defaults = defaultEntries()
        save(context, defaults)
        saveBoxes(context, emptyMap())
        setFocusedType(context, defaults.first().type)
    }

    /** Tracker and Step Sequencer edit/play the same song data through one shared playback
     *  engine, so they share a single Cut Itself value even though each shows its own toggle. */
    fun chokeKey(type: ModuleType): ModuleType = if (type == ModuleType.STEP_SEQUENCER) ModuleType.TRACKER else type

    fun isChokeEnabled(context: Context, type: ModuleType): Boolean =
        prefs(context).getBoolean(KEY_CHOKE_PREFIX + chokeKey(type).name, false)

    fun setChokeEnabled(context: Context, type: ModuleType, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHOKE_PREFIX + chokeKey(type).name, enabled).apply()
    }

    fun load(context: Context): MutableList<ModuleEntry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null)
        if (raw.isNullOrBlank()) {
            return defaultEntries().toMutableList()
        }
        val loaded = raw.split("|").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size != 2) return@mapNotNull null
            val type = try { ModuleType.valueOf(parts[0]) } catch (e: IllegalArgumentException) { return@mapNotNull null }
            ModuleEntry(type, parts[1].toFloatOrNull() ?: defaultHeight(type))
        }
        return if (loaded.isEmpty()) mutableListOf() else loaded.toMutableList()
    }

    fun save(context: Context, entries: List<ModuleEntry>) {
        val raw = entries.joinToString("|") { "${it.type.name}:${it.heightDp}" }
        prefs(context).edit().putString(KEY_ENTRIES, raw).apply()
    }

    fun workspace(context: Context): WorkspaceLayout =
        WorkspaceLayout.fromName(prefs(context).getString(KEY_WORKSPACE, WorkspaceLayout.STACK.name))

    fun setWorkspace(context: Context, layout: WorkspaceLayout) {
        prefs(context).edit().putString(KEY_WORKSPACE, layout.name).apply()
    }

    fun focusedType(context: Context): ModuleType? {
        val raw = prefs(context).getString(KEY_FOCUSED, null) ?: return null
        return try {
            ModuleType.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun setFocusedType(context: Context, type: ModuleType?) {
        prefs(context).edit().putString(KEY_FOCUSED, type?.name).apply()
    }

    fun loadBoxes(context: Context): MutableMap<ModuleType, ModuleBoxFrame> {
        val raw = prefs(context).getString(KEY_BOXES, null) ?: return mutableMapOf()
        return try {
            val array = org.json.JSONArray(raw)
            val loaded = mutableMapOf<ModuleType, ModuleBoxFrame>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val type = try {
                    ModuleType.valueOf(item.getString("type"))
                } catch (e: IllegalArgumentException) {
                    continue
                }
                loaded[type] = ModuleBoxFrame(
                    xDp = item.optDouble("x", 0.0).toFloat(),
                    yDp = item.optDouble("y", 0.0).toFloat(),
                    wDp = item.optDouble("w", defaultHeight(type).toDouble()).toFloat(),
                    hDp = item.optDouble("h", defaultHeight(type).toDouble()).toFloat(),
                )
            }
            loaded
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun saveBoxes(context: Context, boxes: Map<ModuleType, ModuleBoxFrame>) {
        val array = org.json.JSONArray()
        for ((type, box) in boxes) {
            array.put(
                org.json.JSONObject()
                    .put("type", type.name)
                    .put("x", box.xDp.toDouble())
                    .put("y", box.yDp.toDouble())
                    .put("w", box.wDp.toDouble())
                    .put("h", box.hDp.toDouble()),
            )
        }
        prefs(context).edit().putString(KEY_BOXES, array.toString()).apply()
    }

    fun exportJson(context: Context, entries: List<ModuleEntry>): String {
        val array = org.json.JSONArray()
        for (entry in entries) {
            array.put(
                org.json.JSONObject()
                    .put("type", entry.type.name)
                    .put("heightDp", entry.heightDp.toDouble()),
            )
        }
        val choke = org.json.JSONObject()
        for (type in ModuleType.values()) {
            if (chokeKey(type) != type) continue
            choke.put(type.name, isChokeEnabled(context, type))
        }
        val boxes = org.json.JSONArray()
        for ((type, box) in loadBoxes(context)) {
            boxes.put(
                org.json.JSONObject()
                    .put("type", type.name)
                    .put("x", box.xDp.toDouble())
                    .put("y", box.yDp.toDouble())
                    .put("w", box.wDp.toDouble())
                    .put("h", box.hDp.toDouble()),
            )
        }
        return org.json.JSONObject()
            .put("entries", array)
            .put("choke", choke)
            .put("workspace", workspace(context).name)
            .put("focused", focusedType(context)?.name)
            .put("boxes", boxes)
            .toString()
    }

    fun importJson(context: Context, raw: String): MutableList<ModuleEntry>? {
        if (raw.isBlank()) return null
        return try {
            val obj = org.json.JSONObject(raw)
            val array = obj.optJSONArray("entries") ?: return null
            val loaded = (0 until array.length()).mapNotNull { i ->
                val item = array.getJSONObject(i)
                val type = try {
                    ModuleType.valueOf(item.getString("type"))
                } catch (e: IllegalArgumentException) {
                    return@mapNotNull null
                }
                ModuleEntry(type, item.optDouble("heightDp", defaultHeight(type).toDouble()).toFloat())
            }
            if (loaded.isEmpty()) return null
            val choke = obj.optJSONObject("choke")
            if (choke != null) {
                for (key in choke.keys()) {
                    val type = try {
                        ModuleType.valueOf(key)
                    } catch (e: IllegalArgumentException) {
                        continue
                    }
                    setChokeEnabled(context, type, choke.optBoolean(key, false))
                }
            }
            save(context, loaded)
            if (obj.has("workspace")) setWorkspace(context, WorkspaceLayout.fromName(obj.optString("workspace")))
            if (obj.has("focused")) {
                val focusedRaw = obj.optString("focused", "")
                setFocusedType(
                    context,
                    if (focusedRaw.isBlank()) null else try {
                        ModuleType.valueOf(focusedRaw)
                    } catch (e: IllegalArgumentException) {
                        null
                    },
                )
            }
            val boxesArray = obj.optJSONArray("boxes")
            if (boxesArray != null) {
                val boxes = mutableMapOf<ModuleType, ModuleBoxFrame>()
                for (i in 0 until boxesArray.length()) {
                    val item = boxesArray.getJSONObject(i)
                    val boxType = try {
                        ModuleType.valueOf(item.getString("type"))
                    } catch (e: IllegalArgumentException) {
                        continue
                    }
                    boxes[boxType] = ModuleBoxFrame(
                        xDp = item.optDouble("x", 0.0).toFloat(),
                        yDp = item.optDouble("y", 0.0).toFloat(),
                        wDp = item.optDouble("w", defaultHeight(boxType).toDouble()).toFloat(),
                        hDp = item.optDouble("h", defaultHeight(boxType).toDouble()).toFloat(),
                    )
                }
                saveBoxes(context, boxes)
            }
            loaded.toMutableList()
        } catch (e: Exception) {
            null
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
