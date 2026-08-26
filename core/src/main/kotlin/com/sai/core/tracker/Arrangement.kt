package com.sai.core.tracker

/** Playlist timeline: pattern clips replace sequential song walk; audio clips always mix in. */
object Arrangement {
    const val LANES = 8

    fun usesPatternClips(clips: List<PlaylistClip>): Boolean =
        clips.any { it.kind == ClipKind.PATTERN && !it.muted && it.pattern != null }

    fun songStepCount(song: Song, patternLengthAt: (Int) -> Int): Int =
        song.positions.indices.sumOf { Phrase.coerceLength(patternLengthAt(it)) }.coerceAtLeast(1)

    fun clipEnd(clips: List<PlaylistClip>): Int =
        clips.filter { !it.muted }.maxOfOrNull { it.endStep } ?: 0

    fun totalSteps(
        clips: List<PlaylistClip>,
        song: Song,
        patternLengthAt: (Int) -> Int,
    ): Int {
        val clipEnd = clipEnd(clips)
        return if (usesPatternClips(clips)) {
            clipEnd.coerceAtLeast(1)
        } else {
            maxOf(songStepCount(song, patternLengthAt), clipEnd, 1)
        }
    }

    fun sequentialAt(
        song: Song,
        patternLengthAt: (Int) -> Int,
        globalStep: Int,
    ): Pair<Int, Int>? {
        if (song.positions.isEmpty()) return null
        var remaining = globalStep.coerceAtLeast(0)
        for (position in song.positions.indices) {
            val length = Phrase.coerceLength(patternLengthAt(position)).coerceAtLeast(1)
            if (remaining < length) return position to remaining
            remaining -= length
        }
        return null
    }

    fun patternEventsAt(
        clips: List<PlaylistClip>,
        song: Song,
        phrases: Map<Int, Phrase>,
        globalStep: Int,
        patternLengthAt: (Int) -> Int,
    ): List<TriggerEvent> {
        if (!usesPatternClips(clips)) {
            val (position, local) = sequentialAt(song, patternLengthAt, globalStep) ?: return emptyList()
            return eventsForPattern(song, phrases, position, local)
        }
        val events = mutableListOf<TriggerEvent>()
        for (clip in clips) {
            if (clip.kind != ClipKind.PATTERN || !clip.covers(globalStep)) continue
            val pattern = clip.pattern ?: continue
            val length = Phrase.coerceLength(patternLengthAt(pattern)).coerceAtLeast(1)
            val local = (globalStep - clip.startStep) % length
            events.addAll(eventsForPattern(song, phrases, pattern, local))
        }
        return events
    }

    fun audioStartingAt(clips: List<PlaylistClip>, globalStep: Int): List<PlaylistClip> =
        clips.filter { it.kind == ClipKind.AUDIO && !it.muted && it.sampleId != null && it.startStep == globalStep }

    fun playhead(
        clips: List<PlaylistClip>,
        song: Song,
        patternLengthAt: (Int) -> Int,
        globalStep: Int,
    ): Pair<Int, Int> {
        if (usesPatternClips(clips)) {
            val clip = clips.firstOrNull { it.kind == ClipKind.PATTERN && it.covers(globalStep) }
            if (clip?.pattern != null) {
                val length = Phrase.coerceLength(patternLengthAt(clip.pattern)).coerceAtLeast(1)
                return clip.pattern to ((globalStep - clip.startStep) % length)
            }
            return -1 to 0
        }
        return sequentialAt(song, patternLengthAt, globalStep) ?: (-1 to 0)
    }

    fun loopRange(
        clips: List<PlaylistClip>,
        song: Song,
        patternLengthAt: (Int) -> Int,
        mode: LoopMode,
        currentPattern: Int,
        loopStart: Int,
        loopEnd: Int,
    ): IntRange {
        val total = totalSteps(clips, song, patternLengthAt)
        val last = (total - 1).coerceAtLeast(0)
        return when (mode) {
            LoopMode.SONG -> 0..last
            LoopMode.PATTERN -> {
                if (usesPatternClips(clips)) {
                    val matching = clips.filter { it.kind == ClipKind.PATTERN && !it.muted && it.pattern == currentPattern }
                    if (matching.isEmpty()) 0..last
                    else matching.minOf { it.startStep }..((matching.maxOf { it.endStep } - 1).coerceAtLeast(0))
                } else {
                    val start = stepOffset(song, patternLengthAt, currentPattern)
                    val length = Phrase.coerceLength(patternLengthAt(currentPattern)).coerceAtLeast(1)
                    start until (start + length)
                }
            }
            LoopMode.RANGE -> {
                if (usesPatternClips(clips)) {
                    val matching = clips.filter {
                        it.kind == ClipKind.PATTERN && !it.muted && it.pattern != null && it.pattern in loopStart..loopEnd
                    }
                    if (matching.isEmpty()) 0..last
                    else matching.minOf { it.startStep }..((matching.maxOf { it.endStep } - 1).coerceAtLeast(0))
                } else {
                    val start = stepOffset(song, patternLengthAt, loopStart)
                    val endExclusive = stepOffset(song, patternLengthAt, loopEnd) +
                        Phrase.coerceLength(patternLengthAt(loopEnd)).coerceAtLeast(1)
                    start until endExclusive.coerceAtMost(total)
                }
            }
        }.let { range ->
            val first = range.first.coerceIn(0, last)
            val second = range.last.coerceIn(first, last)
            first..second
        }
    }

    fun nextId(clips: List<PlaylistClip>): Int = (clips.maxOfOrNull { it.id } ?: 0) + 1

    fun appendStart(clips: List<PlaylistClip>): Int =
        clips.filter { !it.muted }.maxOfOrNull { it.endStep } ?: 0

    fun lengthStepsFor(frameCount: Int, sampleRate: Int, bpm: Int): Int {
        val stepSec = 60.0 / bpm.coerceAtLeast(1) / 4.0
        val seconds = frameCount.toDouble() / sampleRate.coerceAtLeast(1)
        return kotlin.math.ceil(seconds / stepSec).toInt().coerceAtLeast(1)
    }

    private fun stepOffset(song: Song, patternLengthAt: (Int) -> Int, position: Int): Int {
        val last = song.positions.lastIndex.coerceAtLeast(0)
        val until = position.coerceIn(0, last)
        var offset = 0
        for (i in 0 until until) {
            offset += Phrase.coerceLength(patternLengthAt(i)).coerceAtLeast(1)
        }
        return offset
    }

    private fun eventsForPattern(
        song: Song,
        phrases: Map<Int, Phrase>,
        position: Int,
        localStep: Int,
    ): List<TriggerEvent> {
        val row = song.positions.getOrNull(position) ?: return emptyList()
        return row.mapIndexedNotNull { track, phraseId ->
            val phrase = phraseId?.let { phrases[it] } ?: return@mapIndexedNotNull null
            val step = phrase.steps.getOrNull(localStep) ?: return@mapIndexedNotNull null
            if (step.isEmpty) null else TriggerEvent(track, step)
        }
    }
}
