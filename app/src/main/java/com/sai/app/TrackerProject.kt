package com.sai.app

import android.content.Context
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import org.json.JSONArray
import org.json.JSONObject

private data class ProjectSnapshot(val song: Song, val phrases: Map<Int, Phrase>)

class TrackerProject(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var bpm: Int
        get() = prefs.getInt(KEY_BPM, 120)
        set(value) = prefs.edit().putInt(KEY_BPM, value).apply()

    var name: String
        get() = prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
        set(value) = prefs.edit().putString(KEY_NAME, value.ifBlank { DEFAULT_NAME }).apply()

    /** Global pitch offset applied to song playback (-24..+24 semitones). */
    var pitchSemitones: Int
        get() = prefs.getInt(KEY_PITCH, 0)
        set(value) = prefs.edit().putInt(KEY_PITCH, value.coerceIn(-24, 24)).apply()

    /** Overall mix level for song playback (0..127). */
    var masterVolume: Int
        get() = prefs.getInt(KEY_MASTER, 127)
        set(value) = prefs.edit().putInt(KEY_MASTER, value.coerceIn(0, 127)).apply()

    var song: Song = loadSong()
        private set

    val phrases: MutableMap<Int, Phrase> = loadPhrases()

    private val undoStack = ArrayDeque<ProjectSnapshot>()
    private val redoStack = ArrayDeque<ProjectSnapshot>()

    fun setSongSlot(position: Int, track: Int, phraseId: Int?) {
        pushUndo()
        val rows = song.positions.toMutableList()
        val row = rows[position].toMutableList()
        row[track] = phraseId
        rows[position] = row
        song = song.copy(positions = rows)
        persist()
    }

    fun putPhrase(id: Int, phrase: Phrase) {
        pushUndo()
        phrases[id] = phrase
        persist()
    }

    fun nextPhraseId(): Int = (phrases.keys.maxOrNull() ?: 0) + 1

    // --- Undo / redo ------------------------------------------------------

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(next)
    }

    private fun snapshot() = ProjectSnapshot(song, phrases.toMap())

    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun restore(snapshot: ProjectSnapshot) {
        song = snapshot.song
        phrases.clear()
        phrases.putAll(snapshot.phrases)
        persist()
    }

    // --- Project admin: new / save / load ----------------------------------

    fun resetProject() {
        pushUndo()
        song = Song.empty()
        phrases.clear()
        bpm = 120
        name = DEFAULT_NAME
        pitchSemitones = 0
        masterVolume = 127
        persist()
    }

    fun exportProjectJson(): String {
        val obj = JSONObject()
        obj.put("bpm", bpm)
        obj.put("name", name)
        obj.put("pitchSemitones", pitchSemitones)
        obj.put("masterVolume", masterVolume)
        obj.put("song", JSONObject(encodeSong(song)))
        obj.put("phrases", JSONObject(encodePhrases(phrases)))
        return obj.toString(2)
    }

    fun importProjectJson(raw: String) {
        pushUndo()
        val obj = JSONObject(raw)
        bpm = obj.optInt("bpm", 120)
        name = obj.optString("name", DEFAULT_NAME)
        pitchSemitones = obj.optInt("pitchSemitones", 0)
        masterVolume = obj.optInt("masterVolume", 127)
        song = decodeSong(obj.getJSONObject("song").toString())
        val imported = decodePhrases(obj.getJSONObject("phrases").toString())
        phrases.clear()
        phrases.putAll(imported)
        persist()
    }

    // --- Persistence --------------------------------------------------------

    private fun persist() {
        prefs.edit()
            .putString(KEY_SONG, encodeSong(song))
            .putString(KEY_PHRASES, encodePhrases(phrases))
            .apply()
    }

    private fun loadSong(): Song {
        val raw = prefs.getString(KEY_SONG, null) ?: return Song.empty()
        return decodeSong(raw)
    }

    private fun loadPhrases(): MutableMap<Int, Phrase> {
        val raw = prefs.getString(KEY_PHRASES, null) ?: return mutableMapOf()
        return decodePhrases(raw)
    }

    private fun encodeSong(song: Song): String {
        val rows = JSONArray()
        for (row in song.positions) {
            val jsonRow = JSONArray()
            for (cell in row) jsonRow.put(cell ?: JSONObject.NULL)
            rows.put(jsonRow)
        }
        return JSONObject().put("trackCount", song.trackCount).put("positions", rows).toString()
    }

    private fun decodeSong(raw: String): Song {
        val obj = JSONObject(raw)
        val trackCount = obj.getInt("trackCount")
        val rowsJson = obj.getJSONArray("positions")
        val positions = (0 until rowsJson.length()).map { r ->
            val rowJson = rowsJson.getJSONArray(r)
            (0 until rowJson.length()).map { c -> if (rowJson.isNull(c)) null else rowJson.getInt(c) }
        }
        return Song(trackCount, positions)
    }

    private fun encodePhrases(phrases: Map<Int, Phrase>): String {
        val obj = JSONObject()
        for ((id, phrase) in phrases) {
            val steps = JSONArray()
            for (step in phrase.steps) {
                val s = JSONObject()
                step.note?.let { s.put("note", it) }
                step.instrument?.let { s.put("instrument", it) }
                step.volume?.let { s.put("volume", it) }
                steps.put(s)
            }
            obj.put(id.toString(), steps)
        }
        return obj.toString()
    }

    private fun decodePhrases(raw: String): MutableMap<Int, Phrase> {
        val obj = JSONObject(raw)
        val result = mutableMapOf<Int, Phrase>()
        for (key in obj.keys()) {
            val stepsJson = obj.getJSONArray(key)
            val steps = (0 until stepsJson.length()).map { i ->
                val s = stepsJson.getJSONObject(i)
                Step(
                    note = if (s.has("note")) s.getInt("note") else null,
                    instrument = if (s.has("instrument")) s.getInt("instrument") else null,
                    volume = if (s.has("volume")) s.getInt("volume") else null,
                )
            }
            result[key.toInt()] = Phrase(steps)
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "tracker_project"
        private const val KEY_BPM = "bpm"
        private const val KEY_NAME = "name"
        private const val KEY_PITCH = "pitch_semitones"
        private const val KEY_MASTER = "master_volume"
        private const val KEY_SONG = "song"
        private const val KEY_PHRASES = "phrases"
        private const val DEFAULT_NAME = "Untitled"
        private const val MAX_HISTORY = 20
    }
}
