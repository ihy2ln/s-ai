package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.sai.core.audio.Equalizer
import com.sai.core.audio.InsertFx
import com.sai.core.audio.InsertKind
import com.sai.core.audio.InsertSlot

/** Mixer insert editor: pick an FX kind and set knobs. Saves live params; does not bake the sample. */
object InsertFxMenu {

    fun show(context: Context, title: String, current: InsertSlot, onSave: (InsertSlot) -> Unit) {
        ModuleBrowser.show(
            context = context,
            title = title,
            initialRole = com.sai.core.plugin.PluginRole.EFFECT,
            includeOff = true,
        ) { plugin ->
            if (plugin == null) {
                onSave(InsertSlot())
                return@show
            }
            val kind = try {
                InsertKind.valueOf(plugin.insertKind ?: return@show)
            } catch (e: Exception) {
                return@show
            }
            val params = if (current.kind == kind) current.params else InsertFx.defaults(kind)
            showKnobs(
                context = context,
                title = "$title · ${plugin.name}",
                kind = kind,
                bypassed = current.kind == kind && current.bypassed,
                params = InsertFx.mergeDefaults(kind, params),
                onSave = onSave,
            )
        }
    }

    fun showKnobsForKind(
        context: Context,
        title: String,
        kind: InsertKind,
        current: InsertSlot,
        onSave: (InsertSlot) -> Unit,
    ) {
        if (kind == InsertKind.NONE) {
            onSave(InsertSlot())
            return
        }
        val params = if (current.kind == kind) current.params else InsertFx.defaults(kind)
        showKnobs(
            context = context,
            title = title,
            kind = kind,
            bypassed = current.kind == kind && current.bypassed,
            params = InsertFx.mergeDefaults(kind, params),
            onSave = onSave,
        )
    }

    private fun showKnobs(
        context: Context,
        title: String,
        kind: InsertKind,
        bypassed: Boolean,
        params: Map<String, Double>,
        onSave: (InsertSlot) -> Unit,
    ) {
        val working = params.toMutableMap()
        var bypass = bypassed
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val knobs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        when (kind) {
            InsertKind.FILTER -> {
                knobs.addView(knob(context, "LOW CUT", 20f, 2000f, working, "lowCut") { "%.0fHz".format(it) })
                knobs.addView(knob(context, "HIGH CUT", 1000f, 20000f, working, "highCut") { "%.0fHz".format(it) })
                knobs.addView(knob(context, "CUTOFF", 200f, 18000f, working, "cutoff") { "%.0fHz".format(it) })
                knobs.addView(knob(context, "RES", 0f, 1f, working, "resonance") { "%.2f".format(it) })
                knobs.addView(knob(context, "CRUNCH", 0f, 1f, working, "drive") { "%.2f".format(it) })
                knobs.addView(knob(context, "PITCH", -24f, 24f, working, "pitch") { "%+.0fst".format(it) })
            }
            InsertKind.COMPRESSOR -> {
                knobs.addView(knob(context, "THRES", -60f, 0f, working, "threshold") { "%.0fdB".format(it) })
                knobs.addView(knob(context, "RATIO", 1f, 20f, working, "ratio") { "%.1f:1".format(it) })
                knobs.addView(knob(context, "ATT", 0.5f, 100f, working, "attack") { "%.0fms".format(it) })
                knobs.addView(knob(context, "REL", 10f, 500f, working, "release") { "%.0fms".format(it) })
                knobs.addView(knob(context, "GAIN", -12f, 24f, working, "makeup") { "%.0fdB".format(it) })
            }
            InsertKind.REVERB -> {
                knobs.addView(knob(context, "SIZE", 0f, 1f, working, "size") { "%.2f".format(it) })
                knobs.addView(knob(context, "DAMP", 0f, 1f, working, "damp") { "%.2f".format(it) })
                knobs.addView(knob(context, "MIX", 0f, 1f, working, "mix") { "%.2f".format(it) })
            }
            InsertKind.EQUALIZER -> {
                knobs.addView(knob(context, "LOW CUT", 20f, 2000f, working, "lowCut") { "%.0fHz".format(it) })
                for (i in Equalizer.BAND_FREQS_HZ.indices) {
                    val freq = Equalizer.BAND_FREQS_HZ[i]
                    val label = if (freq >= 1000) "%.1fk".format(freq / 1000) else "%.0f".format(freq)
                    knobs.addView(knob(context, label, -15f, 15f, working, "b$i") { "%.0fdB".format(it) })
                }
                knobs.addView(knob(context, "MID CUT", 0f, 8000f, working, "midCut") { if (it < 21f) "off" else "%.0fHz".format(it) })
                knobs.addView(knob(context, "HIGH CUT", 1000f, 20000f, working, "highCut") { "%.0fHz".format(it) })
            }
            InsertKind.STEREO -> {
                knobs.addView(knob(context, "PAN", -1f, 1f, working, "pan") {
                    if (it < -0.02f) "L%.0f".format(-it * 100) else if (it > 0.02f) "R%.0f".format(it * 100) else "C"
                })
                knobs.addView(knob(context, "WIDTH", 0f, 2f, working, "width") { "%.2f".format(it) })
                knobs.addView(knob(context, "DEPTH", 0f, 1f, working, "depth") {
                    if (it < 0.02f) "front" else "%.0f%% back".format(it * 100)
                })
            }
            InsertKind.DELAY -> {
                knobs.addView(knob(context, "TIME", 20f, 800f, working, "time") { "%.0fms".format(it) })
                knobs.addView(knob(context, "FBK", 0f, 0.9f, working, "feedback") { "%.2f".format(it) })
                knobs.addView(knob(context, "MIX", 0f, 1f, working, "mix") { "%.2f".format(it) })
            }
            InsertKind.DISTORTION -> {
                knobs.addView(knob(context, "DRIVE", 0f, 1f, working, "drive") { "%.2f".format(it) })
                knobs.addView(knob(context, "TONE", 0f, 1f, working, "tone") { "%.2f".format(it) })
                knobs.addView(knob(context, "MIX", 0f, 1f, working, "mix") { "%.2f".format(it) })
            }
            InsertKind.CHORUS -> {
                knobs.addView(knob(context, "RATE", 0.1f, 6f, working, "rate") { "%.2fHz".format(it) })
                knobs.addView(knob(context, "DEPTH", 0f, 1f, working, "depth") { "%.2f".format(it) })
                knobs.addView(knob(context, "MIX", 0f, 1f, working, "mix") { "%.2f".format(it) })
            }
            InsertKind.LIMITER -> {
                knobs.addView(knob(context, "THRES", -18f, 0f, working, "threshold") { "%.0fdB".format(it) })
                knobs.addView(knob(context, "REL", 10f, 400f, working, "release") { "%.0fms".format(it) })
            }
            InsertKind.NONE -> Unit
        }

        val bypassBox = CheckBox(context).apply {
            text = "Bypass"
            isChecked = bypass
            setOnCheckedChangeListener { _, checked -> bypass = checked }
        }
        val setButton = Button(context).apply { text = "Set" }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = "Heard on play and mixdown. Does not rewrite the sample."
                setTextColor(Color.rgb(140, 150, 160))
                textSize = 12f
                setPadding(0, 0, 0, pad / 2)
            })
            addView(HorizontalScrollView(context).apply { addView(knobs) })
            addView(bypassBox)
            addView(setButton)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(content)
            .setNegativeButton("Close", null)
            .create()

        setButton.setOnClickListener {
            onSave(InsertSlot(kind = kind, bypassed = bypass, params = working.toMap()))
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun knob(
        context: Context,
        label: String,
        min: Float,
        max: Float,
        params: MutableMap<String, Double>,
        key: String,
        format: (Float) -> String,
    ) = Knob.labeled(
        context,
        label,
        min,
        max,
        params[key]?.toFloat() ?: min,
        format,
    ) { params[key] = it.toDouble() }
}
