package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sai.core.audio.Compressor
import com.sai.core.audio.Equalizer
import com.sai.core.audio.Filter
import com.sai.core.audio.Reverb
import com.sai.core.audio.StereoShaper
import com.sai.core.audio.Wav

/** What the MX effects read from and write back to - a loaded sampler/synth sound, or an
 *  individual library instrument - so the mixer works on any sound, not just one fixed panel. */
class EffectsTarget(
    val getWav: () -> Wav?,
    val getName: () -> String,
    val onApplied: (Wav) -> Unit,
)

/** The "MX" menu: the mixer, then sound-shaping effects applied to [EffectsTarget]. */
object EffectsMenu {

    fun show(context: Context, target: EffectsTarget) {
        AlertDialog.Builder(context)
            .setTitle("MX")
            .setItems(arrayOf("Mixer", "Synth (Filter)", "Compressor", "Reverb", "Equalizer", "Stereo Shaper")) { _, which ->
                when (which) {
                    0 -> context.startActivity(Intent(context, MixerActivity::class.java))
                    1 -> showFilterDialog(context, target)
                    2 -> showCompressorDialog(context, target)
                    3 -> showReverbDialog(context, target)
                    4 -> showEqualizerDialog(context, target)
                    5 -> showStereoShaperDialog(context, target)
                }
            }
            .show()
    }

    private fun showFilterDialog(context: Context, target: EffectsTarget) {
        var lowCut = 20f
        var highCut = 20000f
        var cutoff = 8000f
        var resonance = 0.2f
        var drive = 0f
        var pitch = 0f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "LOW CUT", 20f, 2000f, lowCut, { "%.0fHz".format(it) }) { lowCut = it })
            addView(Knob.labeled(context, "HIGH CUT", 1000f, 20000f, highCut, { "%.0fHz".format(it) }) { highCut = it })
            addView(Knob.labeled(context, "CUTOFF", 200f, 18000f, cutoff, { "%.0fHz".format(it) }) { cutoff = it })
            addView(Knob.labeled(context, "RES", 0f, 1f, resonance, { "%.2f".format(it) }) { resonance = it })
            addView(Knob.labeled(context, "CRUNCH", 0f, 1f, drive, { "%.2f".format(it) }) { drive = it })
            addView(Knob.labeled(context, "PITCH", -24f, 24f, pitch, { "%+.0fst".format(it) }) { pitch = it })
        }

        effectDialog(context, "Synth / Filter", knobs, target,
            process = { wav -> Filter.apply(wav, lowCut.toDouble(), highCut.toDouble(), cutoff.toDouble(), resonance.toDouble(), drive.toDouble(), pitch.toDouble()) })
    }

    private fun showCompressorDialog(context: Context, target: EffectsTarget) {
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

        effectDialog(context, "Compressor", knobs, target,
            process = { wav -> Compressor.apply(wav, threshold.toDouble(), ratio.toDouble(), attack.toDouble(), release.toDouble(), makeup.toDouble()) })
    }

    private fun showReverbDialog(context: Context, target: EffectsTarget) {
        var size = 0.5f
        var damp = 0.5f
        var mix = 0.3f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "SIZE", 0f, 1f, size, { "%.2f".format(it) }) { size = it })
            addView(Knob.labeled(context, "DAMP", 0f, 1f, damp, { "%.2f".format(it) }) { damp = it })
            addView(Knob.labeled(context, "MIX", 0f, 1f, mix, { "%.2f".format(it) }) { mix = it })
        }

        effectDialog(context, "Reverb", knobs, target,
            process = { wav -> Reverb.apply(wav, size.toDouble(), damp.toDouble(), mix.toDouble()) })
    }

    private fun showEqualizerDialog(context: Context, target: EffectsTarget) {
        val bandGains = FloatArray(Equalizer.BAND_FREQS_HZ.size)
        var lowCut = 20f
        var midCut = 0f
        var highCut = 20000f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "LOW CUT", 20f, 2000f, lowCut, { "%.0fHz".format(it) }) { lowCut = it })
            for (i in Equalizer.BAND_FREQS_HZ.indices) {
                val freq = Equalizer.BAND_FREQS_HZ[i]
                val label = if (freq >= 1000) "%.1fk".format(freq / 1000) else "%.0f".format(freq)
                addView(Knob.labeled(context, label, -15f, 15f, bandGains[i], { "%.0fdB".format(it) }) { bandGains[i] = it })
            }
            addView(Knob.labeled(context, "MID CUT", 0f, 8000f, midCut, { if (it < 21f) "off" else "%.0fHz".format(it) }) { midCut = it })
            addView(Knob.labeled(context, "HIGH CUT", 1000f, 20000f, highCut, { "%.0fHz".format(it) }) { highCut = it })
        }

        effectDialog(context, "Equalizer", knobs, target,
            process = { wav -> Equalizer.apply(wav, DoubleArray(bandGains.size) { i -> bandGains[i].toDouble() }, lowCut.toDouble(), midCut.toDouble(), highCut.toDouble()) })
    }

    private fun showStereoShaperDialog(context: Context, target: EffectsTarget) {
        var pan = 0f
        var width = 1f
        var depth = 0f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "PAN", -1f, 1f, pan, { if (it < -0.02f) "L%.0f".format(-it * 100) else if (it > 0.02f) "R%.0f".format(it * 100) else "C" }) { pan = it })
            addView(Knob.labeled(context, "WIDTH", 0f, 2f, width, { "%.2f".format(it) }) { width = it })
            addView(Knob.labeled(context, "DEPTH", 0f, 1f, depth, { if (it < 0.02f) "front" else "%.0f%% back".format(it * 100) }) { depth = it })
        }

        effectDialog(context, "Stereo Shaper", knobs, target,
            process = { wav -> StereoShaper.apply(wav, pan.toDouble(), width.toDouble(), depth.toDouble()) })
    }

    /** Shared dialog chrome: a row of knobs plus Preview (non-destructive) / Apply (writes back via the target) / Close. */
    private fun effectDialog(
        context: Context,
        title: String,
        knobsRow: LinearLayout,
        target: EffectsTarget,
        process: (Wav) -> Wav,
    ) {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val previewButton = Button(context).apply { text = "Preview" }
        val applyButton = Button(context).apply { text = "Apply" }
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(previewButton)
            addView(applyButton)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "${target.getName()} - Preview or Apply."
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
            val wav = target.getWav()
            if (wav == null) {
                Toast.makeText(context, "No sound loaded", Toast.LENGTH_SHORT).show()
            } else {
                AudioPlayback.playOneShot(process(wav), context = context)
            }
        }
        applyButton.setOnClickListener {
            val wav = target.getWav()
            if (wav == null) {
                Toast.makeText(context, "No sound loaded", Toast.LENGTH_SHORT).show()
            } else {
                target.onApplied(process(wav))
                Toast.makeText(context, "Applied", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
