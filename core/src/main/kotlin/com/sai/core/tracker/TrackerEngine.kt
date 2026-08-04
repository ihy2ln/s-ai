package com.sai.core.tracker

data class TriggerEvent(val track: Int, val step: Step)

class TrackerEngine(
    private val song: Song,
    private val phrases: Map<Int, Phrase>,
) {
    var songPosition: Int = 0
        private set
    var stepIndex: Int = 0
        private set

    fun reset() {
        songPosition = 0
        stepIndex = 0
    }

    fun advance(): List<TriggerEvent> {
        if (song.positions.isEmpty()) return emptyList()

        val row = song.positions[songPosition]
        val events = row.mapIndexedNotNull { track, phraseId ->
            val phrase = phraseId?.let { phrases[it] } ?: return@mapIndexedNotNull null
            val step = phrase.steps.getOrNull(stepIndex) ?: return@mapIndexedNotNull null
            if (step.isEmpty) null else TriggerEvent(track, step)
        }

        stepIndex++
        if (stepIndex >= Phrase.STEP_COUNT) {
            stepIndex = 0
            songPosition++
            if (songPosition >= song.positions.size) songPosition = 0
        }

        return events
    }
}
