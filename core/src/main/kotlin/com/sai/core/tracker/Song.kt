package com.sai.core.tracker

data class Song(
    val trackCount: Int = TRACK_COUNT,
    val positions: List<List<Int?>> = List(DEFAULT_LENGTH) { List(TRACK_COUNT) { null } },
) {
    companion object {
        const val TRACK_COUNT = 8
        const val DEFAULT_LENGTH = 32

        fun empty() = Song()
    }
}
