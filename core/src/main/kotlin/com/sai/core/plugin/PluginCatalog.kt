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
 * One catalog entry. [name] is the display name. [engineId] is the string written onto a mixer
 * chain slot (later VST waves keep adding catalog rows that point at engines). [insertKind] is an
 * [com.sai.core.audio.InsertKind] name for mixer FX. [homeModule] is a Home [ModuleType] name.
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
    val jobs: List<PluginJob> = emptyList(),
    val purpose: String = "",
    val abbrev: String = "",
    val engineId: String = id,
    val defaultParams: Map<String, Double> = emptyMap(),
    val presets: List<PluginPreset> = emptyList(),
    val aliasOf: String? = null,
) {
    val displayName: String get() = name
    val canAddToHome: Boolean get() = !homeModule.isNullOrBlank()
    val canInsertOnMixer: Boolean get() = !insertKind.isNullOrBlank()

    fun tileLine(): String {
        val job = purpose.ifBlank { subtitle }
        return if (jobs.isEmpty()) job else "${jobs.first().label} · $job"
    }
}

data class PluginQuery(
    val text: String = "",
    val role: PluginRole? = null,
    val format: PluginFormat? = null,
    val category: PluginCategory? = null,
    val job: PluginJob? = null,
    val enabledIds: Set<String>? = null,
)

/** First-class in-app plugin list. Existing Home modules are catalog entries so they stay visible. */
object PluginCatalog {

    val all: List<PluginDescriptor> by lazy {
        homeModulesRaw + instrumentModules + effectModules + oneKnobModules
    }

    private val byIdMap by lazy { all.associateBy { it.id } }

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
            if (query.job != null && query.job !in plugin.jobs) return@filter false
            if (query.enabledIds != null && plugin.role != PluginRole.HOME && plugin.id !in query.enabledIds) {
                return@filter false
            }
            if (needle.isEmpty()) return@filter true
            plugin.name.lowercase().contains(needle) ||
                plugin.vendor.lowercase().contains(needle) ||
                plugin.subtitle.lowercase().contains(needle) ||
                plugin.purpose.lowercase().contains(needle) ||
                plugin.abbrev.lowercase().contains(needle) ||
                plugin.engineId.lowercase().contains(needle) ||
                plugin.category.label.lowercase().contains(needle) ||
                plugin.format.badge.lowercase().contains(needle) ||
                plugin.id.lowercase().contains(needle) ||
                plugin.jobs.any { it.label.lowercase().contains(needle) } ||
                (plugin.aliasOf?.lowercase()?.contains(needle) == true)
        }
    }

    fun categoriesFor(role: PluginRole?): List<PluginCategory> {
        val source = if (role == null) all else all.filter { it.role == role }
        return source.map { it.category }.distinct()
    }

    fun exportJson(): String {
        val plugins = all.joinToString(",") { it.toJson() }
        val chains = ChainPresets.all.joinToString(",") { preset ->
            val slots = preset.slots.joinToString(",") { slot ->
                """{"kind":${esc(slot.kind.name)},"engineId":${esc(slot.engineId)},"bypassed":${slot.bypassed},"params":${mapJson(slot.params)}}"""
            }
            """{"id":${esc(preset.id)},"name":${esc(preset.name)},"job":${esc(preset.job.name)},"purpose":${esc(preset.purpose)},"slots":[$slots]}"""
        }
        val knobs = OneKnobs.all.joinToString(",") { spec ->
            """{"id":${esc(spec.id)},"name":${esc(spec.name)},"kind":${esc(spec.kind.name)},"purpose":${esc(spec.purpose)},"jobs":${jobsJson(spec.jobs)}}"""
        }
        return """{"plugins":[$plugins],"chainPresets":[$chains],"oneKnobs":[$knobs]}"""
    }

    private fun PluginDescriptor.toJson(): String = buildString {
        append("{")
        append("\"id\":${esc(id)},")
        append("\"displayName\":${esc(name)},")
        append("\"vendor\":${esc(vendor)},")
        append("\"format\":${esc(format.name)},")
        append("\"kind\":${esc(role.name)},")
        append("\"category\":${esc(category.name)},")
        append("\"jobs\":${jobsJson(jobs)},")
        append("\"purpose\":${esc(purpose.ifBlank { subtitle })},")
        append("\"abbrev\":${esc(abbrev)},")
        append("\"engineId\":${esc(engineId.ifBlank { id })},")
        append("\"params\":${mapJson(defaultParams)},")
        append("\"presets\":[")
        append(presets.joinToString(",") { """{"id":${esc(it.id)},"name":${esc(it.name)},"params":${mapJson(it.params)}}""" })
        append("]")
        if (insertKind != null) append(",\"insertKind\":${esc(insertKind)}")
        if (homeModule != null) append(",\"homeModule\":${esc(homeModule)}")
        if (aliasOf != null) append(",\"aliasOf\":${esc(aliasOf)}")
        append("}")
    }

    private fun jobsJson(jobs: List<PluginJob>): String =
        jobs.joinToString(",", "[", "]") { esc(it.name) }

    private fun mapJson(map: Map<String, Double>): String =
        map.entries.joinToString(",", "{", "}") { (k, v) -> "${esc(k)}:$v" }

    private fun esc(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
        append('"')
    }

    private val vocalMix = listOf(PluginJob.VOCALS, PluginJob.MIX)
    private val guitarMix = listOf(PluginJob.GUITAR, PluginJob.MIX)
    private val drumsMix = listOf(PluginJob.DRUMS, PluginJob.MIX)
    private val instMidi = listOf(PluginJob.INSTRUMENTS, PluginJob.MIDI)

    private val homeModulesRaw = listOf(
        desc("home.sampler", "Sampler", PluginFormat.BUILTIN, PluginRole.HOME, PluginCategory.HOME, "Waveform, slices, record", 0xFFE61E63.toInt(), homeModule = "SAMPLER", purpose = "Sample editor", abbrev = "SMP"),
        desc("home.synth", "Synth", PluginFormat.BUILTIN, PluginRole.HOME, PluginCategory.HOME, "Filter, ADSR, live keys", 0xFF26C6DA.toInt(), homeModule = "SYNTH", purpose = "User-WAV keys", abbrev = "SYN"),
        desc("home.pads", "Pads", PluginFormat.BUILTIN, PluginRole.HOME, PluginCategory.HOME, "4×4 sample pad bank", 0xFF9C27B0.toInt(), homeModule = "PADS", purpose = "Pad bank", abbrev = "PAD"),
        desc("home.tracker", "Tracker", PluginFormat.BUILTIN, PluginRole.HOME, PluginCategory.HOME, "32-position song grid", 0xFF4CAF50.toInt(), homeModule = "TRACKER", purpose = "Song grid", abbrev = "TRK"),
        desc("home.channel-rack", "Channel Rack", PluginFormat.BUILTIN, PluginRole.HOME, PluginCategory.HOME, "Step sequencer + mixer route", 0xFFFFC107.toInt(), homeModule = "STEP_SEQUENCER", purpose = "Step sequencer", abbrev = "RCK"),
    )

    private val instrumentModules = listOf(
        desc("vst3.pulse-keys", "Pulse Keys", PluginFormat.VST3, PluginRole.INSTRUMENT, PluginCategory.KEYS, "Square keys with a resonant filter", 0xFF5C6BC0.toInt(), homeModule = "PULSE_KEYS", jobs = instMidi, purpose = "Square keys", abbrev = "PLS"),
        desc("vst2.saw-lead", "Saw Lead", PluginFormat.VST2, PluginRole.INSTRUMENT, PluginCategory.LEADS, "Bright saw lead, fast attack", 0xFFFF7043.toInt(), homeModule = "SAW_LEAD", jobs = listOf(PluginJob.INSTRUMENTS), purpose = "Bright lead", abbrev = "SAW"),
        desc("vst3.sub-bass", "Sub Bass", PluginFormat.VST3, PluginRole.INSTRUMENT, PluginCategory.BASS, "Sine/triangle sub with a slow filter", 0xFF00897B.toInt(), homeModule = "SUB_BASS", jobs = listOf(PluginJob.INSTRUMENTS), purpose = "Sine sub", abbrev = "SUB"),
        desc("vst2.pluck", "Pluck", PluginFormat.VST2, PluginRole.INSTRUMENT, PluginCategory.KEYS, "Short decaying plucked tone", 0xFF8D6E63.toInt(), homeModule = "PLUCK", jobs = listOf(PluginJob.INSTRUMENTS), purpose = "Short pluck", abbrev = "PLK"),
        desc("vst3.warm-pad", "Warm Pad", PluginFormat.VST3, PluginRole.INSTRUMENT, PluginCategory.PADS, "Stacked sine/triangle pad", 0xFF7E57C2.toInt(), homeModule = "WARM_PAD", jobs = listOf(PluginJob.INSTRUMENTS), purpose = "Stacked pad", abbrev = "PAD"),
        desc("vst2.click-kit", "Click Kit", PluginFormat.VST2, PluginRole.INSTRUMENT, PluginCategory.PERCUSSION, "Sine thud + click for rack rows", 0xFFEF5350.toInt(), homeModule = "CLICK_KIT", jobs = listOf(PluginJob.DRUMS, PluginJob.INSTRUMENTS), purpose = "Click + thud", abbrev = "CLK"),
        desc("vst2.bassline", "Bassline", PluginFormat.VST2, PluginRole.INSTRUMENT, PluginCategory.BASS, "303-style resonant saw bass", 0xFF43A047.toInt(), homeModule = "BASSLINE", jobs = listOf(PluginJob.INSTRUMENTS), purpose = "303-style bass", abbrev = "303"),
        desc("vst3.supersaw", "SuperSaw", PluginFormat.VST3, PluginRole.INSTRUMENT, PluginCategory.LEADS, "Detuned stacked saws", 0xFFFFA726.toInt(), homeModule = "SUPERSAW", jobs = instMidi, purpose = "Stacked saws", abbrev = "SSW"),
        desc(
            "vst3.keys", "Keys", PluginFormat.VST3, PluginRole.INSTRUMENT, PluginCategory.KEYS,
            "User WAVs only — opens Synth", 0xFF26C6DA.toInt(),
            homeModule = "SYNTH", jobs = instMidi, purpose = "User WAV keys (no bank)", abbrev = "KEY",
            engineId = "home.synth", aliasOf = "home.synth",
        ),
        desc("vst2.arp", "Arp", PluginFormat.VST2, PluginRole.INSTRUMENT, PluginCategory.LEADS, "Major-arpeggio note FX voice", 0xFF26A69A.toInt(), homeModule = "ARP", jobs = instMidi, purpose = "Arpeggiated square", abbrev = "ARP"),
        desc("vst3.scale", "Scale", PluginFormat.VST3, PluginRole.INSTRUMENT, PluginCategory.UTILITY, "Scale reference — no audio engine", 0xFF78909C.toInt(), homeModule = "SCALE", jobs = instMidi, purpose = "Scale degrees", abbrev = "SCL"),
    )

    private val effectModules = listOf(
        desc("vst3.filter", "Filter", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.TONE, "Low/high cut, cutoff, crunch, pitch", 0xFF29B6F6.toInt(), insertKind = "FILTER", jobs = listOf(PluginJob.MIX, PluginJob.GUITAR, PluginJob.VOCALS), purpose = "Tone sculpt", abbrev = "FL"),
        desc("vst3.compressor", "Compressor", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.DYNAMICS, "Threshold, ratio, attack, release", 0xFF66BB6A.toInt(), insertKind = "COMPRESSOR", jobs = listOf(PluginJob.VOCALS, PluginJob.DRUMS, PluginJob.MIX), purpose = "Level control", abbrev = "CP"),
        desc(
            "vst2.reverb", "Reverb", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.SPACE, "Size, damp, mix, duck", 0xFF26A69A.toInt(),
            insertKind = "REVERB", jobs = listOf(PluginJob.VOCALS, PluginJob.GUITAR, PluginJob.MIX),
            purpose = "Room / plate", abbrev = "RV",
            presets = listOf(PluginPreset("vocal-verb", "Vocal Verb", mapOf("size" to 0.4, "mix" to 0.22, "duck" to 0.55))),
        ),
        desc("vst3.equalizer", "Equalizer", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.TONE, "Seven bands plus cuts", 0xFF42A5F5.toInt(), insertKind = "EQUALIZER", jobs = listOf(PluginJob.VOCALS, PluginJob.GUITAR, PluginJob.DRUMS, PluginJob.MIX), purpose = "Broad EQ", abbrev = "EQ"),
        desc("vst2.stereo", "Stereo Shaper", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.UTILITY, "Pan, width, depth", 0xFFAB47BC.toInt(), insertKind = "STEREO", jobs = listOf(PluginJob.MIX), purpose = "Width / pan", abbrev = "ST"),
        desc("vst3.delay", "Delay", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.SPACE, "Time, feedback, mix", 0xFF26C6DA.toInt(), insertKind = "DELAY", homeModule = "DELAY", jobs = listOf(PluginJob.VOCALS, PluginJob.GUITAR, PluginJob.MIX), purpose = "Echo taps", abbrev = "DL"),
        desc("vst2.distort", "Distort", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.TONE, "Drive, tone, mix", 0xFFFF7043.toInt(), insertKind = "DISTORTION", homeModule = "DISTORT", jobs = listOf(PluginJob.GUITAR, PluginJob.DRUMS), purpose = "Clip grit", abbrev = "DS"),
        desc("vst3.chorus", "Chorus", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.MODULATION, "Rate, depth, mix", 0xFFEC407A.toInt(), insertKind = "CHORUS", homeModule = "CHORUS", jobs = listOf(PluginJob.VOCALS, PluginJob.GUITAR), purpose = "Detune swirl", abbrev = "CH"),
        desc("vst2.limiter", "Limiter", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.DYNAMICS, "Ceiling, release", 0xFFFFA726.toInt(), insertKind = "LIMITER", homeModule = "LIMITER", jobs = listOf(PluginJob.MIX), purpose = "Peak ceiling", abbrev = "LM"),
        desc(
            "vst3.slapback", "Slapback", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.SPACE, "Short slap echo", 0xFF4DD0E1.toInt(),
            insertKind = "DELAY", jobs = guitarMix + PluginJob.VOCALS, purpose = "Short slap echo", abbrev = "SL",
            engineId = "vst3.delay",
            defaultParams = mapOf("time" to 95.0, "feedback" to 0.08, "mix" to 0.16),
            aliasOf = "vst3.delay",
        ),
        desc(
            "vst2.drive", "Drive", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.TONE, "Softer clip than Distort", 0xFFFF8A65.toInt(),
            insertKind = "DISTORTION", jobs = listOf(PluginJob.GUITAR), purpose = "Amp-like clip", abbrev = "DR",
            engineId = "vst2.distort", aliasOf = "vst2.distort",
            defaultParams = mapOf("drive" to 0.32, "tone" to 0.5, "mix" to 0.28),
        ),
        desc("vst3.tape", "Tape", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.TONE, "Saturation and slow wow", 0xFFA1887F.toInt(), insertKind = "TAPE", jobs = listOf(PluginJob.GUITAR, PluginJob.VOCALS, PluginJob.MIX), purpose = "Tape sat", abbrev = "TP"),
        desc("vst2.phaser", "Phaser", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.MODULATION, "Slow allpass sweep", 0xFF7E57C2.toInt(), insertKind = "PHASER", jobs = guitarMix, purpose = "Allpass sweep", abbrev = "PH"),
        desc("vst3.crush", "Crush", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.TONE, "Bit and rate reduction", 0xFF8D6E63.toInt(), insertKind = "CRUSH", jobs = listOf(PluginJob.DRUMS, PluginJob.INSTRUMENTS), purpose = "Bit crush", abbrev = "CR"),
        desc("vst3.tune", "Tune", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.UTILITY, "Pitch toward a note (manual)", 0xFF66BB6A.toInt(), insertKind = "TUNE", homeModule = "TUNER", jobs = listOf(PluginJob.VOCALS, PluginJob.GUITAR), purpose = "Toward-note pitch", abbrev = "TN"),
        desc("vst2.deess", "DeEss", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.DYNAMICS, "Tame harsh esses", 0xFF81C784.toInt(), insertKind = "DEESS", jobs = listOf(PluginJob.VOCALS), purpose = "Sibilance cut", abbrev = "ES"),
        desc("vst2.amp", "Amp", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.TONE, "Drive into a dark tone", 0xFFFF7043.toInt(), insertKind = "AMP", jobs = listOf(PluginJob.GUITAR), purpose = "Amp body", abbrev = "AM"),
        desc("vst3.gate", "Gate", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.DYNAMICS, "Downward expander", 0xFF9CCC65.toInt(), insertKind = "GATE", jobs = listOf(PluginJob.DRUMS, PluginJob.VOCALS), purpose = "Noise gate", abbrev = "GT"),
        desc(
            "vst3.punch", "Punch", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.DYNAMICS, "Fast compressor punch", 0xFFAED581.toInt(),
            insertKind = "COMPRESSOR", jobs = drumsMix + PluginJob.VOCALS, purpose = "Fast punch", abbrev = "PN",
            engineId = "vst3.compressor", aliasOf = "vst3.compressor",
            defaultParams = mapOf("threshold" to -18.0, "ratio" to 5.0, "attack" to 3.0, "makeup" to 3.0),
        ),
        desc(
            "vst3.duck", "Duck", PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.DYNAMICS, "Fast compressor (no sidechain bus)", 0xFF26A69A.toInt(),
            insertKind = "COMPRESSOR", jobs = vocalMix, purpose = "Fast ducking comp", abbrev = "DK",
            engineId = "vst3.compressor", aliasOf = "vst3.compressor",
            defaultParams = mapOf("threshold" to -20.0, "ratio" to 6.0, "attack" to 2.0, "release" to 120.0, "makeup" to 1.0),
        ),
        desc(
            "vst2.vocal-verb", "Vocal Verb", PluginFormat.VST2, PluginRole.EFFECT, PluginCategory.SPACE, "Ducked room for vocals", 0xFF80CBC4.toInt(),
            insertKind = "REVERB", jobs = listOf(PluginJob.VOCALS), purpose = "Ducked vocal room", abbrev = "VV",
            engineId = "vst2.reverb", aliasOf = "vst2.reverb",
            defaultParams = mapOf("size" to 0.4, "damp" to 0.45, "mix" to 0.22, "duck" to 0.55),
        ),
    )

    private val oneKnobModules: List<PluginDescriptor> = OneKnobs.all.map { spec ->
        desc(
            spec.id, spec.name, PluginFormat.VST3, PluginRole.EFFECT, PluginCategory.UTILITY,
            spec.purpose, 0xFF90A4AE.toInt(),
            insertKind = spec.kind.name, jobs = spec.jobs, purpose = spec.purpose,
            abbrev = spec.name.take(2).uppercase(), engineId = spec.id,
        )
    }

    private fun desc(
        id: String,
        name: String,
        format: PluginFormat,
        role: PluginRole,
        category: PluginCategory,
        subtitle: String,
        color: Int,
        insertKind: String? = null,
        homeModule: String? = null,
        jobs: List<PluginJob> = emptyList(),
        purpose: String = "",
        abbrev: String = "",
        engineId: String = id,
        defaultParams: Map<String, Double> = emptyMap(),
        presets: List<PluginPreset> = emptyList(),
        aliasOf: String? = null,
    ): PluginDescriptor {
        val vendor = when (role) {
            PluginRole.HOME -> "S.Ai"
            PluginRole.INSTRUMENT -> "S.Ai Instruments"
            PluginRole.EFFECT -> "S.Ai Effects"
        }
        return PluginDescriptor(
            id = id,
            name = name,
            vendor = vendor,
            format = format,
            role = role,
            category = category,
            subtitle = subtitle,
            insertKind = insertKind,
            homeModule = homeModule,
            tileColor = color,
            jobs = jobs,
            purpose = purpose,
            abbrev = abbrev,
            engineId = engineId,
            defaultParams = defaultParams,
            presets = presets,
            aliasOf = aliasOf,
        )
    }
}
