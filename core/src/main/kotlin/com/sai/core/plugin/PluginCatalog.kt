package com.sai.core.plugin

/** How a module is presented. Built-in modules stay first-class; VST2/VST3 are in-app plugins. */
enum class PluginFormat {
    BUILTIN,
    VST2,
    VST3,
    ;

    val badge: String get() = when (this) {
        BUILTIN -> "IN"
        VST2 -> "VST2"
        VST3 -> "VST3"
    }
}

enum class PluginRole {
    INSTRUMENT,
    EFFECT,
    HOME,
}

enum class PluginCategory {
    HOME,
    KEYS,
    BASS,
    LEADS,
    PADS,
    PERCUSSION,
    DYNAMICS,
    SPACE,
    TONE,
    MODULATION,
    UTILITY,
    ;

    val label: String get() = when (this) {
        HOME -> "Home"
        KEYS -> "Keys"
        BASS -> "Bass"
        LEADS -> "Leads"
        PADS -> "Pads"
        PERCUSSION -> "Percussion"
        DYNAMICS -> "Dynamics"
        SPACE -> "Space"
        TONE -> "Tone"
        MODULATION -> "Modulation"
        UTILITY -> "Utility"
    }
}

/**
 * One catalog entry. [insertKind] is an [com.sai.core.audio.InsertKind] name for effect plugins
 * that can sit on a mixer strip. [homeModule] is a Home [ModuleType] name when the plugin can
 * be added as a working module. Built-in Home modules use both empty insertKind and a homeModule.
 */
data class PluginDescriptor(
    val id: String,
    val name: String,
    val vendor: String,
    val format: PluginFormat,
    val role: PluginRole,
    val category: PluginCategory,
    val subtitle: String,
    val insertKind: String? = null,
    val homeModule: String? = null,
    val tileColor: Int = 0xFF3D5A80.toInt(),
) {
    val canAddToHome: Boolean get() = !homeModule.isNullOrBlank()
    val canInsertOnMixer: Boolean get() = !insertKind.isNullOrBlank()
}

data class PluginQuery(
    val text: String = "",
    val role: PluginRole? = null,
    val format: PluginFormat? = null,
    val category: PluginCategory? = null,
    val enabledIds: Set<String>? = null,
)

/** First-class in-app plugin list. Existing Home modules are catalog entries so they stay visible. */
object PluginCatalog {

    val all: List<PluginDescriptor> = listOf(
        // Built-in Home modules — never removed from the catalog.
        PluginDescriptor(
            id = "home.sampler",
            name = "Sampler",
            vendor = "S.Ai",
            format = PluginFormat.BUILTIN,
            role = PluginRole.HOME,
            category = PluginCategory.HOME,
            subtitle = "Waveform, slices, record",
            homeModule = "SAMPLER",
            tileColor = 0xFFE61E63.toInt(),
        ),
        PluginDescriptor(
            id = "home.synth",
            name = "Synth",
            vendor = "S.Ai",
            format = PluginFormat.BUILTIN,
            role = PluginRole.HOME,
            category = PluginCategory.HOME,
            subtitle = "Filter, ADSR, live keys",
            homeModule = "SYNTH",
            tileColor = 0xFF26C6DA.toInt(),
        ),
        PluginDescriptor(
            id = "home.pads",
            name = "Pads",
            vendor = "S.Ai",
            format = PluginFormat.BUILTIN,
            role = PluginRole.HOME,
            category = PluginCategory.HOME,
            subtitle = "4×4 sample pad bank",
            homeModule = "PADS",
            tileColor = 0xFF9C27B0.toInt(),
        ),
        PluginDescriptor(
            id = "home.tracker",
            name = "Tracker",
            vendor = "S.Ai",
            format = PluginFormat.BUILTIN,
            role = PluginRole.HOME,
            category = PluginCategory.HOME,
            subtitle = "32-position song grid",
            homeModule = "TRACKER",
            tileColor = 0xFF4CAF50.toInt(),
        ),
        PluginDescriptor(
            id = "home.channel-rack",
            name = "Channel Rack",
            vendor = "S.Ai",
            format = PluginFormat.BUILTIN,
            role = PluginRole.HOME,
            category = PluginCategory.HOME,
            subtitle = "Step sequencer + mixer route",
            homeModule = "STEP_SEQUENCER",
            tileColor = 0xFFFFC107.toInt(),
        ),

        // Instrument plugins (VST2 / VST3 style in-app voices).
        PluginDescriptor(
            id = "vst3.pulse-keys",
            name = "Pulse Keys",
            vendor = "S.Ai Instruments",
            format = PluginFormat.VST3,
            role = PluginRole.INSTRUMENT,
            category = PluginCategory.KEYS,
            subtitle = "Square keys with a resonant filter",
            homeModule = "PULSE_KEYS",
            tileColor = 0xFF5C6BC0.toInt(),
        ),
        PluginDescriptor(
            id = "vst2.saw-lead",
            name = "Saw Lead",
            vendor = "S.Ai Instruments",
            format = PluginFormat.VST2,
            role = PluginRole.INSTRUMENT,
            category = PluginCategory.LEADS,
            subtitle = "Bright saw lead, fast attack",
            homeModule = "SAW_LEAD",
            tileColor = 0xFFFF7043.toInt(),
        ),
        PluginDescriptor(
            id = "vst3.sub-bass",
            name = "Sub Bass",
            vendor = "S.Ai Instruments",
            format = PluginFormat.VST3,
            role = PluginRole.INSTRUMENT,
            category = PluginCategory.BASS,
            subtitle = "Sine/triangle sub with a slow filter",
            homeModule = "SUB_BASS",
            tileColor = 0xFF00897B.toInt(),
        ),
        PluginDescriptor(
            id = "vst2.pluck",
            name = "Pluck",
            vendor = "S.Ai Instruments",
            format = PluginFormat.VST2,
            role = PluginRole.INSTRUMENT,
            category = PluginCategory.KEYS,
            subtitle = "Short decaying plucked tone",
            homeModule = "PLUCK",
            tileColor = 0xFF8D6E63.toInt(),
        ),
        PluginDescriptor(
            id = "vst3.warm-pad",
            name = "Warm Pad",
            vendor = "S.Ai Instruments",
            format = PluginFormat.VST3,
            role = PluginRole.INSTRUMENT,
            category = PluginCategory.PADS,
            subtitle = "Stacked sine/triangle pad",
            homeModule = "WARM_PAD",
            tileColor = 0xFF7E57C2.toInt(),
        ),
        PluginDescriptor(
            id = "vst2.click-kit",
            name = "Click Kit",
            vendor = "S.Ai Instruments",
            format = PluginFormat.VST2,
            role = PluginRole.INSTRUMENT,
            category = PluginCategory.PERCUSSION,
            subtitle = "Sine thud + click for rack rows",
            homeModule = "CLICK_KIT",
            tileColor = 0xFFEF5350.toInt(),
        ),

        // Mixer insert effects — existing processors, plus new delay/distort/chorus/limiter.
        PluginDescriptor(
            id = "vst3.filter",
            name = "Filter",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST3,
            role = PluginRole.EFFECT,
            category = PluginCategory.TONE,
            subtitle = "Low/high cut, cutoff, crunch, pitch",
            insertKind = "FILTER",
            tileColor = 0xFF29B6F6.toInt(),
        ),
        PluginDescriptor(
            id = "vst3.compressor",
            name = "Compressor",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST3,
            role = PluginRole.EFFECT,
            category = PluginCategory.DYNAMICS,
            subtitle = "Threshold, ratio, attack, release",
            insertKind = "COMPRESSOR",
            tileColor = 0xFF66BB6A.toInt(),
        ),
        PluginDescriptor(
            id = "vst2.reverb",
            name = "Reverb",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST2,
            role = PluginRole.EFFECT,
            category = PluginCategory.SPACE,
            subtitle = "Size, damp, mix",
            insertKind = "REVERB",
            tileColor = 0xFF26A69A.toInt(),
        ),
        PluginDescriptor(
            id = "vst3.equalizer",
            name = "Equalizer",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST3,
            role = PluginRole.EFFECT,
            category = PluginCategory.TONE,
            subtitle = "Seven bands plus cuts",
            insertKind = "EQUALIZER",
            tileColor = 0xFF42A5F5.toInt(),
        ),
        PluginDescriptor(
            id = "vst2.stereo",
            name = "Stereo Shaper",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST2,
            role = PluginRole.EFFECT,
            category = PluginCategory.UTILITY,
            subtitle = "Pan, width, depth",
            insertKind = "STEREO",
            tileColor = 0xFFAB47BC.toInt(),
        ),
        PluginDescriptor(
            id = "vst3.delay",
            name = "Delay",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST3,
            role = PluginRole.EFFECT,
            category = PluginCategory.SPACE,
            subtitle = "Time, feedback, mix",
            insertKind = "DELAY",
            homeModule = "DELAY",
            tileColor = 0xFF26C6DA.toInt(),
        ),
        PluginDescriptor(
            id = "vst2.distort",
            name = "Distort",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST2,
            role = PluginRole.EFFECT,
            category = PluginCategory.TONE,
            subtitle = "Drive, tone, mix",
            insertKind = "DISTORTION",
            homeModule = "DISTORT",
            tileColor = 0xFFFF7043.toInt(),
        ),
        PluginDescriptor(
            id = "vst3.chorus",
            name = "Chorus",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST3,
            role = PluginRole.EFFECT,
            category = PluginCategory.MODULATION,
            subtitle = "Rate, depth, mix",
            insertKind = "CHORUS",
            homeModule = "CHORUS",
            tileColor = 0xFFEC407A.toInt(),
        ),
        PluginDescriptor(
            id = "vst2.limiter",
            name = "Limiter",
            vendor = "S.Ai Effects",
            format = PluginFormat.VST2,
            role = PluginRole.EFFECT,
            category = PluginCategory.DYNAMICS,
            subtitle = "Ceiling, release",
            insertKind = "LIMITER",
            homeModule = "LIMITER",
            tileColor = 0xFFFFA726.toInt(),
        ),
    )

    private val byIdMap = all.associateBy { it.id }

    fun byId(id: String): PluginDescriptor? = byIdMap[id]

    fun homeModules(): List<PluginDescriptor> = all.filter { it.role == PluginRole.HOME }

    fun instruments(): List<PluginDescriptor> = all.filter { it.role == PluginRole.INSTRUMENT }

    fun effects(): List<PluginDescriptor> = all.filter { it.role == PluginRole.EFFECT }

    fun toggleable(): List<PluginDescriptor> = all.filter { it.role != PluginRole.HOME }

    fun search(query: PluginQuery): List<PluginDescriptor> {
        val needle = query.text.trim().lowercase()
        return all.filter { plugin ->
            if (query.role != null && plugin.role != query.role) return@filter false
            if (query.format != null && plugin.format != query.format) return@filter false
            if (query.category != null && plugin.category != query.category) return@filter false
            if (query.enabledIds != null && plugin.role != PluginRole.HOME && plugin.id !in query.enabledIds) {
                return@filter false
            }
            if (needle.isEmpty()) return@filter true
            plugin.name.lowercase().contains(needle) ||
                plugin.vendor.lowercase().contains(needle) ||
                plugin.subtitle.lowercase().contains(needle) ||
                plugin.category.label.lowercase().contains(needle) ||
                plugin.format.badge.lowercase().contains(needle) ||
                plugin.id.lowercase().contains(needle)
        }
    }

    fun categoriesFor(role: PluginRole?): List<PluginCategory> {
        val source = if (role == null) all else all.filter { it.role == role }
        return source.map { it.category }.distinct()
    }
}
