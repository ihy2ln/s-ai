package com.sai.core.tracker

data class TriggerEvent(val track: Int, val step: Step)

class TrackerEngine(
    private val song: Song,
    private val phrases: Map<Int, Phrase>,
    private val patternLengthAt: (Int) -> Int = { Phrase.DEFAULT_LENGTH },
    loopStart: Int = 0,
    loopEnd: Int = (song.positions.size - 1).coerceAtLeast(0),
) {
    var loopStart: Int = loopStart
        private set
    var loopEnd: Int = loopEnd
        private set

    var songPosition: Int = clampedStart()
        private set
    var stepIndex: Int = 0
        private set

    fun setLoop(start: Int, end: Int) {
        loopStart = start
        loopEnd = end
        if (song.positions.isEmpty()) return
        if (songPosition !in clampedStart()..clampedEnd()) {
            songPosition = clampedStart()
            stepIndex = 0
        }
    }

    fun reset() {
        songPosition = clampedStart()
        stepIndex = 0
    }

    fun patternLength(): Int = lengthAt(songPosition)

    fun advance(): List<TriggerEvent> {
        if (song.positions.isEmpty()) return emptyList()

        val row = song.positions[songPosition]
        val events = row.mapIndexedNotNull { track, phraseId ->
            val phrase = phraseId?.let { phrases[it] } ?: return@mapIndexedNotNull null
            val step = phrase.steps.getOrNull(stepIndex) ?: return@mapIndexedNotNull null
            if (step.isEmpty) null else TriggerEvent(track, step)
        }

        stepIndex++
        if (stepIndex >= lengthAt(songPosition)) {
            stepIndex = 0
            songPosition++
            val end = clampedEnd()
            val start = clampedStart()
            if (songPosition > end || songPosition >= song.positions.size) {
                songPosition = start
            }
        }

        return events
    }

    private fun lengthAt(position: Int): Int =
        Phrase.coerceLength(patternLengthAt(position)).coerceIn(1, Phrase.MAX_STEPS)

    private fun clampedStart(): Int {
        if (song.positions.isEmpty()) return 0
        val end = clampedEnd()
        return loopStart.coerceIn(0, end)
    }

    private fun clampedEnd(): Int {
        if (song.positions.isEmpty()) return 0
        return loopEnd.coerceIn(0, song.positions.lastIndex)
    }
}
