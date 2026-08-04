package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sai.core.audio.Compressor
import com.sai.core.audio.Equalizer
import com.sai.core.audio.Filter
import com.sai.core.audio.Reverb
import com.sai.core.audio.Wav

/** The "MX" menu: sound-shaping effects (synth filter, compressor, reverb, EQ) plus the step sequencer,
 *  all applied on-demand to whatever sample is currently loaded in [SamplerPanelView]. */
object EffectsMenu {

    fun show(context: Context, samplerPanel: SamplerPanelView) {
        AlertDialog.Builder(context)
            .setTitle("MX")
            .setItems(arrayOf("Synth (Filter)", "Compressor", "Reverb", "Equalizer", "Step Sequencer")) { _, which ->
                when (which) {
                    0 -> showFilterDialog(context, samplerPanel)
                    1 -> showCompressorDialog(context, samplerPanel)
                    2 -> showReverbDialog(context, samplerPanel)
                    3 -> showEqualizerDialog(context, samplerPanel)
                    4 -> context.startActivity(Intent(context, StepSequencerActivity::class.java))
                }
            }
            .show()
    }

    private fun showFilterDialog(context: Context, panel: SamplerPanelView) {
        var cutoff = 8000f
        var resonance = 0.2f
        var drive = 0f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "CUTOFF", 200f, 18000f, cutoff, { "%.0fHz".format(it) }) { cutoff = it })
            addView(Knob.labeled(context, "RES", 0f, 1f, resonance, { "%.2f".format(it) }) { resonance = it })
            addView(Knob.labeled(context, "CRUNCH", 0f, 1f, drive, { "%.2f".format(it) }) { drive = it })
        }

        effectDialog(context, "Synth / Filter", knobs, panel,
            process = { wav -> Filter.apply(wav, cutoff.toDouble(), resonance.toDouble(), drive.toDouble()) })
    }

    private fun showCompressorDialog(context: Context, panel: SamplerPanelView) {
        var threshold = -18f
        var ratio = 4f
        var attack = 5f
        var release = 60f
        var makeup = 0f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "THRES", -60f, 0f, threshold, { "%.0fdB".format(it) }) { threshold = it })
            addView(Knob.labeled(context, "RATIO", 1f, 20f, ratio, { "%.1f:1".format(it) }) { ratio = it })
            addView(Knob.labeled(context, "ATT", 0.5f, 100f, attack, { "%.0fms".format(it) }) { attack = it })
            addView(Knob.labeled(context, "REL", 10f, 500f, release, { "%.0fms".format(it) }) { release = it })
            addView(Knob.labeled(context, "GAIN", -12f, 24f, makeup, { "%.0fdB".format(it) }) { makeup = it })
        }

        effectDialog(context, "Compressor", knobs, panel,
            process = { wav -> Compressor.apply(wav, threshold.toDouble(), ratio.toDouble(), attack.toDouble(), release.toDouble(), makeup.toDouble()) })
    }

    private fun showReverbDialog(context: Context, panel: SamplerPanelView) {
        var size = 0.5f
        var damp = 0.5f
        var mix = 0.3f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "SIZE", 0f, 1f, size, { "%.2f".format(it) }) { size = it })
            addView(Knob.labeled(context, "DAMP", 0f, 1f, damp, { "%.2f".format(it) }) { damp = it })
            addView(Knob.labeled(context, "MIX", 0f, 1f, mix, { "%.2f".format(it) }) { mix = it })
        }

        effectDialog(context, "Reverb", knobs, panel,
            process = { wav -> Reverb.apply(wav, size.toDouble(), damp.toDouble(), mix.toDouble()) })
    }

    private fun showEqualizerDialog(context: Context, panel: SamplerPanelView) {
        var low = 0f
        var mid = 0f
        var high = 0f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "LOW", -15f, 15f, low, { "%.0fdB".format(it) }) { low = it })
            addView(Knob.labeled(context, "MID", -15f, 15f, mid, { "%.0fdB".format(it) }) { mid = it })
            addView(Knob.labeled(context, "HIGH", -15f, 15f, high, { "%.0fdB".format(it) }) { high = it })
        }

        effectDialog(context, "Equalizer", knobs, panel,
            process = { wav -> Equalizer.apply(wav, low.toDouble(), mid.toDouble(), high.toDouble()) })
    }

    /** Shared dialog chrome: a row of knobs plus Preview (non-destructive) / Apply (bakes into the loaded sample) / Close. */
    private fun effectDialog(
        context: Context,
        title: String,
        knobsRow: LinearLayout,
        panel: SamplerPanelView,
        process: (Wav) -> Wav,
    ) {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val previewButton = Button(context).apply { text = "Preview" }
        val applyButton = Button(context).apply { text = "Apply to Sample" }
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(previewButton)
            addView(applyButton)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Load a sample first, then Preview or Apply."
                setTextColor(Color.rgb(140, 150, 160))
                textSize = 12f
                setPadding(0, 0, 0, pad / 2)
            })
            addView(HorizontalScrollView(context).apply { addView(knobsRow) })
            addView(buttonsRow)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(content)
            .setNegativeButton("Close", null)
            .create()

        previewButton.setOnClickListener {
            val wav = panel.currentWav()
            if (wav == null) {
                Toast.makeText(context, "No sample loaded", Toast.LENGTH_SHORT).show()
            } else {
                AudioPlayback.playOneShot(process(wav))
            }
        }
        applyButton.setOnClickListener {
            val wav = panel.currentWav()
            if (wav == null) {
                Toast.makeText(context, "No sample loaded", Toast.LENGTH_SHORT).show()
            } else {
                panel.load(process(wav), panel.currentSourceName())
                Toast.makeText(context, "Applied", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
