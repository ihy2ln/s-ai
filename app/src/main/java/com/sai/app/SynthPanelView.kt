package com.sai.app

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.sai.core.audio.Filter
import com.sai.core.audio.Wav

/** The SYNTH panel: loads a sample and shapes it with the 6-knob synth filter (same DSP as MX > Synth),
 *  live in the middle of the home screen instead of behind a dialog. */
class SynthPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val sampleNameLabel: TextView
    private val waveform: WaveformView

    private var wav: Wav? = null
    private var sourceName: String = ""

    private var lowCut = 20f
    private var highCut = 20000f
    private var cutoff = 8000f
    private var resonance = 0.2f
    private var drive = 0f
    private var pitch = 0f

    /** Invoked with the processed sound when the user wants to keep it as a usable instrument. */
    var onSaveToLibrary: ((sourceName: String, wav: Wav) -> Unit)? = null

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density

        sampleNameLabel = TextView(context).apply {
            text = "No sample loaded"
            setTextColor(Color.WHITE)
        }

        waveform = WaveformView(context)

        val knobsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(Knob.labeled(context, "LOW CUT", 20f, 2000f, lowCut, { "%.0fHz".format(it) }) { lowCut = it })
            addView(Knob.labeled(context, "HIGH CUT", 1000f, 20000f, highCut, { "%.0fHz".format(it) }) { highCut = it })
            addView(Knob.labeled(context, "CUTOFF", 200f, 18000f, cutoff, { "%.0fHz".format(it) }) { cutoff = it })
            addView(Knob.labeled(context, "RES", 0f, 1f, resonance, { "%.2f".format(it) }) { resonance = it })
            addView(Knob.labeled(context, "CRUNCH", 0f, 1f, drive, { "%.2f".format(it) }) { drive = it })
            addView(Knob.labeled(context, "PITCH", -24f, 24f, pitch, { "%+.0fst".format(it) }) { pitch = it })
        }

        val previewButton = Button(context).apply { text = "Preview"; setOnClickListener { preview() } }
        val applyButton = Button(context).apply { text = "Apply"; setOnClickListener { applyInPlace() } }
        val saveButton = Button(context).apply { text = "Save to Library"; setOnClickListener { saveToLibrary() } }
        val buttonsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(previewButton)
            addView(applyButton)
            addView(saveButton)
        }

        addView(sampleNameLabel)
        addView(waveform, LayoutParams(LayoutParams.MATCH_PARENT, (80 * density).toInt()))
        addView(HorizontalScrollView(context).apply { addView(knobsRow) })
        addView(buttonsRow)
    }

    fun load(newWav: Wav, name: String) {
        wav = newWav
        sourceName = name.substringBeforeLast('.')
        sampleNameLabel.text = name
        refreshWaveform()
    }

    private fun refreshWaveform() {
        val current = wav
        waveform.channels = current?.channels ?: 1
        waveform.samples = current?.samples ?: ShortArray(0)
    }

    private fun processed(): Wav? {
        val current = wav ?: return null
        return Filter.apply(current, lowCut.toDouble(), highCut.toDouble(), cutoff.toDouble(), resonance.toDouble(), drive.toDouble(), pitch.toDouble())
    }

    private fun preview() {
        val result = processed() ?: return
        val choke = ModuleLayoutStore.isChokeEnabled(context, ModuleType.SYNTH)
        AudioPlayback.playOneShot(result, context = context, chokeGroup = if (choke) "synth" else null)
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
}
