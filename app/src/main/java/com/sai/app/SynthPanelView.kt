package com.sai.app

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sai.core.audio.Envelope
import com.sai.core.audio.Filter
import com.sai.core.audio.Oscillator
import com.sai.core.audio.Waveform
import com.sai.core.audio.Wav
import kotlin.math.pow

/** The SYNTH panel: loads a sample and shapes it with the 6-knob synth filter (same DSP as MX > Synth),
 *  live in the middle of the home screen instead of behind a dialog. */
class SynthPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val sampleNameLabel: TextView
    private val hintLabel: TextView
    private val waveform: WaveformView

    private var wav: Wav? = null
    private var sourceName: String = ""

    private var lowCut = 20f
    private var highCut = 20000f
    private var cutoff = 8000f
    private var resonance = 0.2f
    private var drive = 0f
    private var pitch = 0f
    private var attack = 0.005f
    private var decay = 0.08f
    private var sustain = 0.85f
    private var release = 0.12f
    private var keyboardOctave = 4

    /** Invoked with the processed sound when the user wants to keep it as a usable instrument. */
    var onSaveToLibrary: ((sourceName: String, wav: Wav) -> Unit)? = null

    /** Invoked with the processed sound to add it to the sample library and pick a module for it. */
    var onAddAsSample: ((sourceName: String, wav: Wav) -> Unit)? = null

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density

        sampleNameLabel = TextView(context).apply {
            text = "No sample loaded"
            setTextColor(AppTheme.textPrimary)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        hintLabel = TextView(context).apply {
            text = "Tap the wave to play"
            setTextColor(AppTheme.textMuted)
            textSize = 10f
        }

        val waveformsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            for (waveform in Waveform.entries) {
                addView(Ui.compactButton(context, Oscillator.displayName(waveform)) { loadWaveform(waveform) })
            }
        }

        waveform = WaveformView(context).apply {
            setOnClickListener { preview() }
        }

        val knobsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(Knob.labeled(context, "LOW CUT", 20f, 2000f, lowCut, { "%.0fHz".format(it) }) { lowCut = it })
            addView(Knob.labeled(context, "HIGH CUT", 1000f, 20000f, highCut, { "%.0fHz".format(it) }) { highCut = it })
            addView(Knob.labeled(context, "CUTOFF", 200f, 18000f, cutoff, { "%.0fHz".format(it) }) { cutoff = it })
            addView(Knob.labeled(context, "RES", 0f, 1f, resonance, { "%.2f".format(it) }) { resonance = it })
            addView(Knob.labeled(context, "CRUNCH", 0f, 1f, drive, { "%.2f".format(it) }) { drive = it })
            addView(Knob.labeled(context, "PITCH", -24f, 24f, pitch, { "%+.0fst".format(it) }) { pitch = it })
        }

        attack = SynthStore.attack(context)
        decay = SynthStore.decay(context)
        sustain = SynthStore.sustain(context)
        release = SynthStore.release(context)
        val envRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(Knob.labeled(context, "ATK", 0f, 1f, attack, { "%.2fs".format(it) }) {
                attack = it
                SynthStore.setAttack(context, it)
            })
            addView(Knob.labeled(context, "DEC", 0f, 1f, decay, { "%.2fs".format(it) }) {
                decay = it
                SynthStore.setDecay(context, it)
            })
            addView(Knob.labeled(context, "SUS", 0f, 1f, sustain, { "%.2f".format(it) }) {
                sustain = it
                SynthStore.setSustain(context, it)
            })
            addView(Knob.labeled(context, "REL", 0f, 1.5f, release, { "%.2fs".format(it) }) {
                release = it
                SynthStore.setRelease(context, it)
            })
        }

        val keyboard = buildKeyboard()

        val previewButton = Ui.compactButton(context, "Preview") { preview() }
        val applyButton = Ui.compactButton(context, "Apply") { applyInPlace() }
        val addSampleButton = Ui.compactButton(context, "Add as Sample") { addAsSample() }
        val saveButton = Ui.compactButton(context, "Save to Library") { saveToLibrary() }
        val buttonsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(previewButton)
            addView(applyButton)
            addView(addSampleButton)
            addView(saveButton)
        }

        addView(sampleNameLabel)
        addView(hintLabel)
        addView(waveformsRow)
        addView(waveform, LayoutParams(LayoutParams.MATCH_PARENT, (80 * density).toInt()))
        addView(HorizontalScrollView(context).apply {
            isNestedScrollingEnabled = false
            addView(knobsRow)
        })
        addView(HorizontalScrollView(context).apply {
            isNestedScrollingEnabled = false
            addView(envRow)
        })
        addView(keyboard)
        addView(HorizontalScrollView(context).apply {
            isNestedScrollingEnabled = false
            addView(buttonsRow)
        })
    }

    fun load(newWav: Wav, name: String) {
        wav = newWav
        sourceName = name.substringBeforeLast('.')
        sampleNameLabel.text = name
        refreshWaveform()
    }

    private fun loadWaveform(waveform: Waveform) {
        val name = Oscillator.displayName(waveform)
        load(Oscillator.generate(waveform), name)
        preview()
    }

    private fun refreshWaveform() {
        val current = wav
        waveform.channels = current?.channels ?: 1
        waveform.samples = current?.samples ?: ShortArray(0)
    }

    private fun processed(): Wav? {
        val current = wav ?: return null
        val filtered = Filter.apply(current, lowCut.toDouble(), highCut.toDouble(), cutoff.toDouble(), resonance.toDouble(), drive.toDouble(), pitch.toDouble())
        return Envelope.apply(filtered, attack.toDouble(), decay.toDouble(), sustain.toDouble(), release.toDouble())
    }

    private fun preview() {
        playLive(60)
    }

    private fun playLive(midiNote: Int) {
        val result = processed() ?: return
        val choke = ModuleLayoutStore.isChokeEnabled(context, ModuleType.SYNTH)
        val rate = 2.0.pow((midiNote - 60) / 12.0).toFloat()
        AudioPlayback.playOneShot(result, rate, context, chokeGroup = if (choke) "synth" else null)
    }

    private fun buildKeyboard(): LinearLayout {
        val density = resources.displayMetrics.density
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val black = setOf(1, 3, 6, 8, 10)
        val octaveLabel = TextView(context).apply {
            text = "C$keyboardOctave"
            setTextColor(AppTheme.textPrimary)
            gravity = android.view.Gravity.CENTER
            textSize = 11f
        }
        val keys = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(Ui.compactButton(context, "−") {
                keyboardOctave = (keyboardOctave - 1).coerceIn(1, 7)
                octaveLabel.text = "C$keyboardOctave"
            })
            addView(octaveLabel)
            addView(Ui.compactButton(context, "+") {
                keyboardOctave = (keyboardOctave + 1).coerceIn(1, 7)
                octaveLabel.text = "C$keyboardOctave"
            })
            for ((index, name) in names.withIndex()) {
                addView(Button(context).apply {
                    text = name
                    textSize = 10f
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    setPadding((6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt())
                    setTextColor(if (index in black) AppTheme.textPrimary else AppTheme.canvas)
                    setBackgroundColor(if (index in black) AppTheme.pianoBlack else AppTheme.textPrimary)
                    setOnClickListener { playLive((keyboardOctave + 1) * 12 + index) }
                })
            }
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            addView(TextView(context).apply {
                text = "Keys — POLY stacks notes; MONO cuts the previous one"
                setTextColor(AppTheme.textMuted)
                textSize = 10f
            })
            addView(HorizontalScrollView(context).apply { addView(keys) })
        }
    }

    private fun applyInPlace() {
        val result = processed() ?: return
        wav = result
        refreshWaveform()
    }

    private fun saveToLibrary() {
        val result = processed() ?: return
        onSaveToLibrary?.invoke(sourceName, result)
    }

    private fun addAsSample() {
        val result = processed()
        if (result == null) {
            Toast.makeText(context, "Load a sample or pick a waveform first", Toast.LENGTH_SHORT).show()
            return
        }
        onAddAsSample?.invoke(sourceName, result)
    }
}
