package com.sai.core.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** In-app instrument renderers for VST-style Home modules. */
enum class VoiceKind {
    PULSE_KEYS,
    SAW_LEAD,
    SUB_BASS,
    PLUCK,
    WARM_PAD,
    CLICK_KIT,
    BASSLINE,
    SUPERSAW,
    ARP,
}

object InstrumentVoice {

    fun render(
        kind: VoiceKind,
        midiNote: Int,
        params: Map<String, Double> = emptyMap(),
        sampleRate: Int = Oscillator.DEFAULT_SAMPLE_RATE,
    ): Wav {
        val freq = midiToHz(midiNote)
        val cutoff = params["cutoff"] ?: defaultCutoff(kind)
        val resonance = params["resonance"] ?: 0.2
        val drive = params["drive"] ?: 0.0
        val attack = params["attack"] ?: defaultAttack(kind)
        val release = params["release"] ?: defaultRelease(kind)
        val duration = params["duration"] ?: defaultDuration(kind)
        val amplitude = params["amp"] ?: 0.55

        val raw = when (kind) {
            VoiceKind.PULSE_KEYS -> Oscillator.generate(
                Waveform.SQUARE, sampleRate, 1, duration, freq, amplitude,
            )
            VoiceKind.SAW_LEAD -> Oscillator.generate(
                Waveform.SAW, sampleRate, 1, duration, freq, amplitude,
            )
            VoiceKind.SUB_BASS -> mix(
                Oscillator.generate(Waveform.SINE, sampleRate, 1, duration, freq, amplitude),
                Oscillator.generate(Waveform.TRIANGLE, sampleRate, 1, duration, freq * 2.0, amplitude * 0.25),
            )
            VoiceKind.PLUCK -> Oscillator.generate(
                Waveform.TRIANGLE, sampleRate, 1, duration, freq, amplitude,
            )
            VoiceKind.WARM_PAD -> mix(
                Oscillator.generate(Waveform.SINE, sampleRate, 1, duration, freq, amplitude * 0.7),
                Oscillator.generate(Waveform.TRIANGLE, sampleRate, 1, duration, freq * 1.005, amplitude * 0.45),
            )
            VoiceKind.CLICK_KIT -> clickKit(sampleRate, freq, duration, amplitude)
            VoiceKind.BASSLINE -> Oscillator.generate(
                Waveform.SAW, sampleRate, 1, duration, freq, amplitude,
            )
            VoiceKind.SUPERSAW -> mix(
                Oscillator.generate(Waveform.SAW, sampleRate, 1, duration, freq * 0.993, amplitude * 0.45),
                mix(
                    Oscillator.generate(Waveform.SAW, sampleRate, 1, duration, freq, amplitude * 0.7),
                    Oscillator.generate(Waveform.SAW, sampleRate, 1, duration, freq * 1.007, amplitude * 0.45),
                ),
            )
            VoiceKind.ARP -> arp(sampleRate, midiNote, duration, amplitude)
        }
        val cutoffEnv = when (kind) {
            VoiceKind.BASSLINE -> (params["cutoff"] ?: 900.0)
            else -> cutoff
        }
        val resEnv = when (kind) {
            VoiceKind.BASSLINE -> (params["resonance"] ?: 0.72)
            else -> resonance
        }
        val shaped = Filter.apply(raw, 20.0, 20000.0, cutoffEnv, resEnv, drive, 0.0)
        val sustain = when (kind) {
            VoiceKind.PLUCK, VoiceKind.CLICK_KIT, VoiceKind.ARP -> 0.15
            VoiceKind.WARM_PAD, VoiceKind.SUPERSAW -> 0.8
            VoiceKind.SUB_BASS, VoiceKind.BASSLINE -> 0.9
            else -> 0.7
        }
        return Envelope.apply(shaped, attack, defaultDecay(kind), sustain, release)
    }

    fun kindForHomeModule(homeModule: String): VoiceKind? = when (homeModule) {
        "PULSE_KEYS" -> VoiceKind.PULSE_KEYS
        "SAW_LEAD" -> VoiceKind.SAW_LEAD
        "SUB_BASS" -> VoiceKind.SUB_BASS
        "PLUCK" -> VoiceKind.PLUCK
        "WARM_PAD" -> VoiceKind.WARM_PAD
        "CLICK_KIT" -> VoiceKind.CLICK_KIT
        "BASSLINE" -> VoiceKind.BASSLINE
        "SUPERSAW" -> VoiceKind.SUPERSAW
        "ARP" -> VoiceKind.ARP
        else -> null
    }

    fun midiToHz(midiNote: Int): Double {
        val note = midiNote.coerceIn(0, 127)
        return 440.0 * 2.0.pow((note - 69) / 12.0)
    }

    private fun defaultDuration(kind: VoiceKind): Double = when (kind) {
        VoiceKind.PLUCK, VoiceKind.CLICK_KIT -> 0.45
        VoiceKind.ARP -> 1.2
        VoiceKind.WARM_PAD, VoiceKind.SUPERSAW -> 1.6
        VoiceKind.SUB_BASS, VoiceKind.BASSLINE -> 1.1
        else -> 0.7
    }

    private fun defaultAttack(kind: VoiceKind): Double = when (kind) {
        VoiceKind.PLUCK, VoiceKind.CLICK_KIT, VoiceKind.SAW_LEAD -> 0.005
        VoiceKind.WARM_PAD -> 0.18
        VoiceKind.SUB_BASS -> 0.03
        VoiceKind.BASSLINE -> 0.02
        else -> 0.01
    }

    private fun defaultDecay(kind: VoiceKind): Double = when (kind) {
        VoiceKind.PLUCK, VoiceKind.CLICK_KIT -> 0.12
        else -> 0.08
    }

    private fun defaultRelease(kind: VoiceKind): Double = when (kind) {
        VoiceKind.PLUCK, VoiceKind.CLICK_KIT -> 0.18
        VoiceKind.WARM_PAD -> 0.45
        else -> 0.12
    }

    private fun defaultCutoff(kind: VoiceKind): Double = when (kind) {
        VoiceKind.SUB_BASS -> 420.0
        VoiceKind.PULSE_KEYS -> 2400.0
        VoiceKind.SAW_LEAD -> 6200.0
        VoiceKind.PLUCK -> 3800.0
        VoiceKind.WARM_PAD -> 1800.0
        VoiceKind.CLICK_KIT -> 8000.0
        VoiceKind.BASSLINE -> 900.0
        VoiceKind.SUPERSAW -> 5400.0
        VoiceKind.ARP -> 4200.0
    }

    private fun mix(a: Wav, b: Wav): Wav {
        val frames = minOf(a.frameCount, b.frameCount)
        val channels = a.channels
        val out = ShortArray(frames * channels)
        for (i in out.indices) {
            val sum = a.samples.getOrElse(i) { 0 } + b.samples.getOrElse(i) { 0 }
            out[i] = (sum / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return Wav(a.sampleRate, channels, out)
    }

    private fun clickKit(sampleRate: Int, freq: Double, duration: Double, amplitude: Double): Wav {
        val frames = (sampleRate * duration).toInt().coerceAtLeast(1)
        val samples = ShortArray(frames)
        val click = (sampleRate * 0.008).toInt().coerceAtLeast(4)
        for (frame in 0 until frames) {
            val t = frame.toDouble() / sampleRate
            val thud = sin(2.0 * PI * freq.coerceIn(40.0, 180.0) * t) * expDecay(t, 18.0)
            val noise = if (frame < click) {
                ((frame * 1103515245 + 12345) and 0x7fff) / 32768.0 * 2.0 - 1.0
            } else {
                0.0
            }
            val value = amplitude * (thud * 0.85 + noise * 0.35)
            samples[frame] = (value * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Wav(sampleRate, 1, samples)
    }

    private fun arp(sampleRate: Int, midiNote: Int, duration: Double, amplitude: Double): Wav {
        val steps = intArrayOf(0, 4, 7, 12, 7, 4)
        val slice = (duration / steps.size).coerceAtLeast(0.08)
        var combined: Wav? = null
        for (offset in steps) {
            val tone = Oscillator.generate(
                Waveform.SQUARE,
                sampleRate,
                1,
                slice,
                midiToHz(midiNote + offset),
                amplitude,
            )
            val gated = Envelope.apply(tone, 0.005, 0.04, 0.2, 0.04)
            combined = if (combined == null) gated else concat(combined, gated)
        }
        return combined ?: Oscillator.generate(Waveform.SQUARE, sampleRate, 1, duration, midiToHz(midiNote), amplitude)
    }

    private fun concat(a: Wav, b: Wav): Wav {
        val channels = a.channels
        return Wav(a.sampleRate, channels, a.samples + b.samples)
    }

    private fun expDecay(t: Double, speed: Double): Double = kotlin.math.exp(-speed * t)
}
