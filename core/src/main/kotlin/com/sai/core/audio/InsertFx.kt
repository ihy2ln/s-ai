package com.sai.core.audio

import kotlin.math.pow

enum class InsertKind {
    NONE,
    FILTER,
    COMPRESSOR,
    REVERB,
    EQUALIZER,
    STEREO,
}

/** One mixer insert slot: a kind plus knobs. Inactive when [kind] is NONE or [bypassed]. */
data class InsertSlot(
    val kind: InsertKind = InsertKind.NONE,
    val bypassed: Boolean = false,
    val params: Map<String, Double> = emptyMap(),
) {
    val isActive: Boolean get() = kind != InsertKind.NONE && !bypassed

    fun shortLabel(): String = when {
        kind == InsertKind.NONE -> "fx"
        bypassed -> kindAbbrev().lowercase()
        else -> kindAbbrev()
    }

    private fun kindAbbrev(): String = when (kind) {
        InsertKind.NONE -> "fx"
        InsertKind.FILTER -> "FL"
        InsertKind.COMPRESSOR -> "CP"
        InsertKind.REVERB -> "RV"
        InsertKind.EQUALIZER -> "EQ"
        InsertKind.STEREO -> "ST"
    }

    fun fingerprint(): String {
        if (!isActive) return "off"
        val body = params.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
        return "${kind.name}:$body"
    }
}

/**
 * Live mixer inserts: the same offline processors MX uses on samples, applied at playback
 * and mixdown so a strip's FX is heard without baking into the library.
 */
object InsertFx {

    fun defaults(kind: InsertKind): Map<String, Double> = when (kind) {
        InsertKind.NONE -> emptyMap()
        InsertKind.FILTER -> mapOf(
            "lowCut" to 20.0,
            "highCut" to 20000.0,
            "cutoff" to 8000.0,
            "resonance" to 0.2,
            "drive" to 0.0,
            "pitch" to 0.0,
        )
        InsertKind.COMPRESSOR -> mapOf(
            "threshold" to -18.0,
            "ratio" to 4.0,
            "attack" to 5.0,
            "release" to 60.0,
            "makeup" to 0.0,
        )
        InsertKind.REVERB -> mapOf(
            "size" to 0.5,
            "damp" to 0.5,
            "mix" to 0.3,
        )
        InsertKind.EQUALIZER -> buildMap {
            put("lowCut", 20.0)
            put("midCut", 0.0)
            put("highCut", 20000.0)
            for (i in Equalizer.BAND_FREQS_HZ.indices) put("b$i", 0.0)
        }
        InsertKind.STEREO -> mapOf(
            "pan" to 0.0,
            "width" to 1.0,
            "depth" to 0.0,
        )
    }

    fun mergeDefaults(kind: InsertKind, overlay: Map<String, Double>): Map<String, Double> =
        defaults(kind) + overlay

    fun param(slot: InsertSlot, key: String, fallback: Double): Double =
        slot.params[key] ?: defaults(slot.kind)[key] ?: fallback

    fun apply(wav: Wav, slot: InsertSlot): Wav {
        if (!slot.isActive || wav.frameCount == 0) return wav
        return when (slot.kind) {
            InsertKind.NONE -> wav
            InsertKind.FILTER -> Filter.apply(
                wav,
                param(slot, "lowCut", 20.0),
                param(slot, "highCut", 20000.0),
                param(slot, "cutoff", 8000.0),
                param(slot, "resonance", 0.2),
                param(slot, "drive", 0.0),
                param(slot, "pitch", 0.0),
            )
            InsertKind.COMPRESSOR -> Compressor.apply(
                wav,
                param(slot, "threshold", -18.0),
                param(slot, "ratio", 4.0),
                param(slot, "attack", 5.0),
                param(slot, "release", 60.0),
                param(slot, "makeup", 0.0),
            )
            InsertKind.REVERB -> {
                val size = param(slot, "size", 0.5)
                val damp = param(slot, "damp", 0.5)
                val mix = param(slot, "mix", 0.3)
                Reverb.apply(pad(wav, tailFrames(slot, wav.sampleRate)), size, damp, mix)
            }
            InsertKind.EQUALIZER -> {
                val bands = DoubleArray(Equalizer.BAND_FREQS_HZ.size) { i -> param(slot, "b$i", 0.0) }
                Equalizer.apply(
                    wav,
                    bands,
                    param(slot, "lowCut", 20.0),
                    param(slot, "midCut", 0.0),
                    param(slot, "highCut", 20000.0),
                )
            }
            InsertKind.STEREO -> StereoShaper.apply(
                wav,
                param(slot, "pan", 0.0),
                param(slot, "width", 1.0),
                param(slot, "depth", 0.0),
            )
        }
    }

    /** Extra frames an insert may append (reverb tail, stereo depth delay). */
    fun tailFrames(slot: InsertSlot, sampleRate: Int): Int {
        if (!slot.isActive) return 0
        return when (slot.kind) {
            InsertKind.REVERB -> {
                val size = param(slot, "size", 0.5).coerceIn(0.0, 1.0)
                val mix = param(slot, "mix", 0.3).coerceIn(0.0, 1.0)
                (sampleRate * (0.35 + size * 1.15) * mix).toInt().coerceAtLeast(0)
            }
            InsertKind.STEREO -> {
                val depth = param(slot, "depth", 0.0).coerceIn(0.0, 1.0)
                (depth * 0.02 * sampleRate).toInt()
            }
            else -> 0
        }
    }

    /** Output length relative to input after insert (filter pitch stretch). */
    fun lengthFactor(slot: InsertSlot): Double {
        if (!slot.isActive || slot.kind != InsertKind.FILTER) return 1.0
        val pitch = param(slot, "pitch", 0.0)
        if (pitch == 0.0) return 1.0
        return 2.0.pow(-pitch / 12.0)
    }

    private fun pad(wav: Wav, frames: Int): Wav {
        if (frames <= 0) return wav
        return wav.copy(samples = wav.samples + ShortArray(frames * wav.channels))
    }
}
