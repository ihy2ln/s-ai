package com.sai.core.wiki

/**
 * New-user guide: short linked wiki topics bundled in the app (`assets/wiki/{id}.md`)
 * and mirrored under `docs/wiki/`.
 */
object WikiGuide {

    data class Topic(
        val id: String,
        val title: String,
        val blurb: String,
    )

    const val INDEX_ID = "index"

    val topics: List<Topic> = listOf(
        Topic(INDEX_ID, "Start here", "What S.Ai is, and the shortest path into the rest of the guide."),
        Topic("add-modules", "Add a module", "VST2/VST3-style catalog: search, tiles, and Add Module."),
        Topic("instruments-effects-home", "Instruments vs Effects vs Home", "Which tab to open for instruments, FX, or built-in modules."),
        Topic("rack-vs-mixer", "Channel Rack vs mixer inserts", "Where instruments land vs where effects process."),
        Topic("built-in-modules", "Built-in modules stay", "Sampler, Synth, Tracker, Channel Rack, and Pads are not replaced."),
        Topic("jobs", "Vocals, guitar, drums, mix", "Pick modules by the job, not by plugin format."),
        Topic("not-a-vst-host", "Not a .vst3 host", "In-app catalog engines — not desktop .dll / .vst3 binaries."),
    )

    fun byId(id: String): Topic? = topics.find { it.id == id }

    fun assetPath(id: String): String = "wiki/$id.md"

    fun knownIds(): Set<String> = topics.map { it.id }.toSet()
}
