package com.sai.core.stem

/** Platform-neutral stem splitter configuration. */
data class StemSplitSettings(
    val backend: StemBackend = StemBackend.COMFY_UI,
    val comfyBaseUrl: String = "",
    val comfyApiKey: String = "",
    val demucsModel: String = "htdemucs",
    val pollIntervalMs: Long = 1_500L,
    val requestTimeoutMs: Int = 30_000,
    val maxWaitMs: Long = 20 * 60_000L,
)
