package com.sai.app

import android.content.Context
import com.sai.core.tracker.LoopMode
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Song
import com.sai.core.tracker.Step
import org.json.JSONArray
import org.json.JSONObject

private data class ProjectSnapshot(
    val song: Song,
    val phrases: Map<Int, Phrase>,
    val patternLengths: List<Int>,
    val swing: Int,
)

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

    var swing: Int = prefs.getInt(KEY_SWING, 0).coerceIn(0, 100)
        set(value) {
            field = value.coerceIn(0, 100)
            prefs.edit().putInt(KEY_SWING, field).apply()
        }

    var loopMode: LoopMode = LoopMode.fromName(prefs.getString(KEY_LOOP_MODE, LoopMode.SONG.name))
        set(value) {
            field = value
            prefs.edit().putString(KEY_LOOP_MODE, value.name).apply()
        }

    var loopStart: Int = prefs.getInt(KEY_LOOP_START, 0)
        set(value) {
            field = value.coerceAtLeast(0)
            prefs.edit().putInt(KEY_LOOP_START, field).apply()
        }

    var loopEnd: Int = prefs.getInt(KEY_LOOP_END, Song.DEFAULT_LENGTH - 1)
        set(value) {
            field = value.coerceAtLeast(0)
            prefs.edit().putInt(KEY_LOOP_END, field).apply()
        }

    var song: Song = loadSong()
        private set

    val phrases: MutableMap<Int, Phrase> = loadPhrases()

    private var patternLengths: MutableList<Int> = loadPatternLengths()

    private val undoStack = ArrayDeque<ProjectSnapshot>()
    private val redoStack = ArrayDeque<ProjectSnapshot>()

    fun patternLength(position: Int): Int {
        ensurePatternLengths()
        return Phrase.coerceLength(patternLengths.getOrNull(position) ?: Phrase.DEFAULT_LENGTH)
    }

    fun setPatternLength(position: Int, length: Int) {
        ensurePatternLengths()
        if (position !in patternLengths.indices) return
        pushUndo()
        patternLengths[position] = Phrase.coerceLength(length)
        persist()
    }

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
        phrases[id] = Phrase.fromSteps(phrase.steps)
        persist()
    }

    fun nextPhraseId(): Int = (phrases.keys.maxOrNull() ?: 0) + 1

    /** Copies every phrase on [from] into the next empty song row (or the following row if none). */
    fun duplicatePattern(from: Int): Int? {
        if (from !in song.positions.indices) return null
        val dest = duplicateDestination(from)
        pushUndo()
        var nextId = nextPhraseId()
        val copied = song.positions[from].map { phraseId ->
            val phrase = phraseId?.let { phrases[it] } ?: return@map null
            val id = nextId++
            phrases[id] = Phrase.fromSteps(phrase.steps)
            id
        }
        val rows = song.positions.toMutableList()
        rows[dest] = copied
        song = song.copy(positions = rows)
        ensurePatternLengths()
        patternLengths[dest] = patternLength(from)
        persist()
        return dest
    }

    fun loopBounds(pattern: Int = loopStart): Pair<Int, Int> {
        val last = (song.positions.size - 1).coerceAtLeast(0)
        return when (loopMode) {
            LoopMode.SONG -> 0 to last
            LoopMode.PATTERN -> {
                val p = pattern.coerceIn(0, last)
                p to p
            }
            LoopMode.RANGE -> {
                val start = loopStart.coerceIn(0, last)
                val end = loopEnd.coerceIn(start, last)
                start to end
            }
        }
    }

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

    private fun snapshot() = ProjectSnapshot(song, phrases.toMap(), patternLengths.toList(), swing)

    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun restore(snapshot: ProjectSnapshot) {
        song = snapshot.song
        phrases.clear()
        phrases.putAll(snapshot.phrases)
        patternLengths = snapshot.patternLengths.toMutableList()
        swing = snapshot.swing
        persist()
    }

    // --- Project admin: new / save / load ----------------------------------

    fun resetProject() {
        pushUndo()
        song = Song.empty()
        phrases.clear()
        patternLengths = defaultPatternLengths(Song.DEFAULT_LENGTH)
        bpm = 120
        name = DEFAULT_NAME
        pitchSemitones = 0
        masterVolume = 127
        swing = 0
        loopMode = LoopMode.SONG
        loopStart = 0
        loopEnd = Song.DEFAULT_LENGTH - 1
        persist()
    }

    fun exportProjectJson(): String {
        val obj = JSONObject()
        obj.put("bpm", bpm)
        obj.put("name", name)
        obj.put("pitchSemitones", pitchSemitones)
        obj.put("masterVolume", masterVolume)
        obj.put("swing", swing)
        obj.put("loopMode", loopMode.name)
        obj.put("loopStart", loopStart)
        obj.put("loopEnd", loopEnd)
        obj.put("patternLengths", JSONArray(patternLengths))
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
        swing = obj.optInt("swing", 0).coerceIn(0, 100)
        loopMode = LoopMode.fromName(obj.optString("loopMode", LoopMode.SONG.name))
        loopStart = obj.optInt("loopStart", 0)
        loopEnd = obj.optInt("loopEnd", Song.DEFAULT_LENGTH - 1)
        song = decodeSong(obj.getJSONObject("song").toString())
        val imported = decodePhrases(obj.getJSONObject("phrases").toString())
        phrases.clear()
        phrases.putAll(imported)
        patternLengths = decodePatternLengths(obj.optJSONArray("patternLengths"), song.positions.size)
        persist()
    }

    // --- Persistence --------------------------------------------------------

    private fun persist() {
        prefs.edit()
            .putString(KEY_SONG, encodeSong(song))
            .putString(KEY_PHRASES, encodePhrases(phrases))
            .putString(KEY_PATTERN_LENGTHS, JSONArray(patternLengths).toString())
            .putInt(KEY_SWING, swing)
            .putString(KEY_LOOP_MODE, loopMode.name)
            .putInt(KEY_LOOP_START, loopStart)
            .putInt(KEY_LOOP_END, loopEnd)
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

    private fun loadPatternLengths(): MutableList<Int> {
        val raw = prefs.getString(KEY_PATTERN_LENGTHS, null)
        val count = song.positions.size
        if (raw == null) return defaultPatternLengths(count)
        return try {
            decodePatternLengths(JSONArray(raw), count)
        } catch (e: Exception) {
            defaultPatternLengths(count)
        }
    }

    private fun ensurePatternLengths() {
        val needed = song.positions.size
        while (patternLengths.size < needed) patternLengths.add(Phrase.DEFAULT_LENGTH)
        if (patternLengths.size > needed) {
            patternLengths = patternLengths.take(needed).toMutableList()
        }
    }

    private fun duplicateDestination(from: Int): Int {
        val size = song.positions.size
        for (offset in 1 until size) {
            val index = (from + offset) % size
            if (song.positions[index].all { it == null }) return index
        }
        return (from + 1) % size
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
                step.length?.let { s.put("length", it) }
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
                    length = if (s.has("length")) s.getInt("length") else null,
                )
            }
            result[key.toInt()] = Phrase.fromSteps(steps)
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
        private const val KEY_PATTERN_LENGTHS = "pattern_lengths"
        private const val KEY_SWING = "swing"
        private const val KEY_LOOP_MODE = "loop_mode"
        private const val KEY_LOOP_START = "loop_start"
        private const val KEY_LOOP_END = "loop_end"
        private const val DEFAULT_NAME = "Untitled"
        private const val MAX_HISTORY = 20

        private fun defaultPatternLengths(count: Int) =
            MutableList(count) { Phrase.DEFAULT_LENGTH }

        private fun decodePatternLengths(array: JSONArray?, count: Int): MutableList<Int> {
            val lengths = defaultPatternLengths(count)
            if (array == null) return lengths
            for (i in 0 until minOf(array.length(), count)) {
                lengths[i] = Phrase.coerceLength(array.optInt(i, Phrase.DEFAULT_LENGTH))
            }
            return lengths
        }
    }
}
