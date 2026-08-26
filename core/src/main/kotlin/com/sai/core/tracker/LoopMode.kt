package com.sai.core.tracker

enum class LoopMode {
    SONG,
    PATTERN,
    RANGE,
    ;

    companion object {
        fun fromName(raw: String?): LoopMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SONG
    }
}
