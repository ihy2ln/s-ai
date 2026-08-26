package com.sai.core.tracker

enum class ClipKind {
    PATTERN,
    AUDIO,
}

/** One block on the arrangement timeline. Steps are 16th notes from the start of the playlist. */
data class PlaylistClip(
    val id: Int,
    val kind: ClipKind,
    val lane: Int,
    val startStep: Int,
    val lengthSteps: Int,
    val pattern: Int? = null,
    val sampleId: Int? = null,
    val muted: Boolean = false,
) {
    val endStep: Int get() = startStep + length.coerceAtLeast(1)

    val length: Int get() = lengthSteps.coerceAtLeast(1)

    fun covers(step: Int): Boolean =
        !muted && step >= startStep && step < endStep
}
