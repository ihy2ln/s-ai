package com.sai.core.plugin

import com.sai.core.audio.InsertChain
import com.sai.core.audio.InsertFx
import com.sai.core.audio.InsertKind
import com.sai.core.audio.InsertSlot

enum class PluginJob {
    VOCALS,
    GUITAR,
    DRUMS,
    MIX,
    MIDI,
    INSTRUMENTS,
    ;

    val label: String get() = when (this) {
        VOCALS -> "Vocals"
        GUITAR -> "Guitar"
        DRUMS -> "Drums"
        MIX -> "Mix"
        MIDI -> "MIDI"
        INSTRUMENTS -> "Instruments"
    }
}

data class PluginPreset(
    val id: String,
    val name: String,
    val params: Map<String, Double>,
)

data class ChainPreset(
    val id: String,
    val name: String,
    val job: PluginJob,
    val purpose: String,
    val slots: List<InsertSlot>,
) {
    fun chain(): InsertChain = InsertChain(slots)
}

object ChainPresets {

    val all: List<ChainPreset> = listOf(
        ChainPreset(
            id = "chain.lead-vocal",
            name = "Lead Vocal",
            job = PluginJob.VOCALS,
            purpose = "Tune → DeEss → EQ → Comp → Delay → ducked Reverb",
            slots = listOf(
                slot(InsertKind.TUNE, "vst3.tune", mapOf("amount" to 0.22)),
                slot(InsertKind.DEESS, "vst3.deess", mapOf("amount" to 0.4)),
                slot(InsertKind.EQUALIZER, "vst3.equalizer", mapOf("lowCut" to 80.0, "b5" to 2.0)),
                slot(InsertKind.COMPRESSOR, "vst3.compressor", mapOf("threshold" to -16.0, "ratio" to 3.0, "makeup" to 2.0)),
                slot(InsertKind.DELAY, "vst3.delay", mapOf("time" to 90.0, "feedback" to 0.12, "mix" to 0.18)),
                slot(InsertKind.REVERB, "vst2.reverb", mapOf("size" to 0.4, "mix" to 0.22, "duck" to 0.55)),
            ),
        ),
        ChainPreset(
            id = "chain.vocal-double",
            name = "Vocal Double",
            job = PluginJob.VOCALS,
            purpose = "Chorus and a short delay for a double",
            slots = listOf(
                slot(InsertKind.CHORUS, "vst3.chorus", mapOf("mix" to 0.22, "depth" to 0.35)),
                slot(InsertKind.DELAY, "vst3.delay", mapOf("time" to 70.0, "feedback" to 0.08, "mix" to 0.16)),
                slot(InsertKind.STEREO, "vst2.stereo", mapOf("width" to 1.25)),
            ),
        ),
        ChainPreset(
            id = "chain.guitar-crunch",
            name = "Guitar Crunch",
            job = PluginJob.GUITAR,
            purpose = "Amp → EQ → slap Delay → small Room",
            slots = listOf(
                slot(InsertKind.AMP, "vst2.amp", mapOf("drive" to 0.48, "tone" to 0.4, "mix" to 0.4)),
                slot(InsertKind.EQUALIZER, "vst3.equalizer", mapOf("lowCut" to 90.0, "b3" to 1.5)),
                slot(InsertKind.DELAY, "vst3.delay", mapOf("time" to 95.0, "feedback" to 0.1, "mix" to 0.16)),
                slot(InsertKind.REVERB, "vst2.reverb", mapOf("size" to 0.28, "mix" to 0.18)),
            ),
        ),
        ChainPreset(
            id = "chain.drum-punch",
            name = "Drum Punch",
            job = PluginJob.DRUMS,
            purpose = "Gate → punch Comp → EQ",
            slots = listOf(
                slot(InsertKind.GATE, "vst3.gate", mapOf("threshold" to 0.05)),
                slot(InsertKind.COMPRESSOR, "vst3.compressor", mapOf("threshold" to -18.0, "ratio" to 5.0, "attack" to 3.0, "makeup" to 3.0)),
                slot(InsertKind.EQUALIZER, "vst3.equalizer", mapOf("b0" to 2.0, "b6" to 1.5)),
            ),
        ),
        ChainPreset(
            id = "chain.master-polish",
            name = "Master polish",
            job = PluginJob.MIX,
            purpose = "EQ → Comp → Stereo → Limiter",
            slots = listOf(
                slot(InsertKind.EQUALIZER, "vst3.equalizer", mapOf("lowCut" to 30.0, "b4" to 0.8)),
                slot(InsertKind.COMPRESSOR, "vst3.compressor", mapOf("threshold" to -12.0, "ratio" to 2.0, "makeup" to 1.0)),
                slot(InsertKind.STEREO, "vst2.stereo", mapOf("width" to 1.1)),
                slot(InsertKind.LIMITER, "vst2.limiter", mapOf("threshold" to -1.0)),
            ),
        ),
    )

    fun byId(id: String): ChainPreset? = all.firstOrNull { it.id == id }

    private fun slot(kind: InsertKind, engineId: String, overlay: Map<String, Double>): InsertSlot =
        InsertSlot(
            kind = kind,
            params = InsertFx.mergeDefaults(kind, overlay),
            engineId = engineId,
        )
}

object OneKnobs {

    data class Spec(
        val id: String,
        val name: String,
        val kind: InsertKind,
        val jobs: List<PluginJob>,
        val purpose: String,
        val paramsAt: (Double) -> Map<String, Double>,
    )

    val all: List<Spec> = listOf(
        Spec("knob.brighter", "Brighter", InsertKind.EQUALIZER, listOf(PluginJob.VOCALS, PluginJob.MIX, PluginJob.GUITAR), "High shelf") { a ->
            mapOf("b6" to 6.0 * a, "b7" to 8.0 * a, "highCut" to 20000.0)
        },
        Spec("knob.echo", "Echo", InsertKind.DELAY, listOf(PluginJob.VOCALS, PluginJob.GUITAR, PluginJob.MIX), "Short delay") { a ->
            mapOf("time" to 180.0 + 220.0 * a, "feedback" to 0.12 + 0.25 * a, "mix" to 0.12 + 0.22 * a)
        },
        Spec("knob.punchy", "Punchy", InsertKind.COMPRESSOR, listOf(PluginJob.DRUMS, PluginJob.VOCALS, PluginJob.MIX), "Fast compressor") { a ->
            mapOf("threshold" to -8.0 - 14.0 * a, "ratio" to 2.0 + 6.0 * a, "attack" to 4.0, "makeup" to 4.0 * a)
        },
        Spec("knob.room", "Room", InsertKind.REVERB, listOf(PluginJob.VOCALS, PluginJob.DRUMS, PluginJob.GUITAR), "Small room") { a ->
            mapOf("size" to 0.2 + 0.35 * a, "damp" to 0.45, "mix" to 0.12 + 0.22 * a, "duck" to 0.2)
        },
        Spec("knob.toasty", "Toasty", InsertKind.TAPE, listOf(PluginJob.GUITAR, PluginJob.VOCALS, PluginJob.MIX), "Light tape") { a ->
            mapOf("drive" to 0.2 + 0.45 * a, "wow" to 0.15 * a, "mix" to 0.2 + 0.25 * a)
        },
        Spec("knob.bassy", "Bassy", InsertKind.EQUALIZER, listOf(PluginJob.DRUMS, PluginJob.MIX, PluginJob.INSTRUMENTS), "Low shelf") { a ->
            mapOf("b0" to 8.0 * a, "b1" to 5.0 * a, "lowCut" to 20.0)
        },
        Spec("knob.leveller", "Leveller", InsertKind.COMPRESSOR, listOf(PluginJob.VOCALS, PluginJob.MIX), "Slow leveller") { a ->
            mapOf("threshold" to -10.0 - 12.0 * a, "ratio" to 1.5 + 2.5 * a, "attack" to 40.0, "release" to 180.0, "makeup" to 2.0 * a)
        },
        Spec("knob.dirty", "Dirty", InsertKind.DISTORTION, listOf(PluginJob.GUITAR, PluginJob.DRUMS, PluginJob.INSTRUMENTS), "Gentle grit") { a ->
            mapOf("drive" to 0.2 + 0.55 * a, "tone" to 0.4, "mix" to 0.18 + 0.3 * a)
        },
    )

    fun byId(id: String): Spec? = all.firstOrNull { it.id == id }

    fun slot(id: String, amount: Double): InsertSlot? {
        val spec = byId(id) ?: return null
        val a = amount.coerceIn(0.0, 1.0)
        return InsertSlot(
            kind = spec.kind,
            params = InsertFx.mergeDefaults(spec.kind, spec.paramsAt(a) + mapOf("amount" to a)),
            engineId = spec.id,
        )
    }
}
