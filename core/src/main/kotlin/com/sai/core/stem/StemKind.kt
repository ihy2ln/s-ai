package com.sai.core.stem

/** Stems that can be extracted from a mixed audio source. */
enum class StemKind(val label: String, val librarySuffix: String) {
    VOCALS("Vocals", "vocals"),
    DRUMS("Drums", "drums"),
    BASS("Bass", "bass"),
    OTHER("Other", "other"),
    INSTRUMENTAL("Instrumental", "instrumental"),
}

enum class StemSplitMode(val label: String) {
    TWO_STEM("Vocals + Instrumental"),
    FOUR_STEM("Vocals / Drums / Bass / Other"),
    ;

    fun defaultKinds(): Set<StemKind> = when (this) {
        TWO_STEM -> setOf(StemKind.VOCALS, StemKind.INSTRUMENTAL)
        FOUR_STEM -> setOf(StemKind.VOCALS, StemKind.DRUMS, StemKind.BASS, StemKind.OTHER)
    }
}

enum class StemBackend(val label: String) {
    COMFY_UI("ComfyUI (your PC)"),
    CLOUD("Cloud (coming soon)"),
}
