package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast

/** Global project sound controls: pitch transpose and master volume for overall playback. */
object ProjectSoundMenu {

    fun show(context: Context) {
        val project = TrackerProjectStore.get(context)
        var pitch = project.pitchSemitones.toFloat()
        var master = project.masterVolume.toFloat()
        var buffer = LatencyStore.bufferMultiplier(context).toFloat()
        var latency = LatencyStore.recordOffsetMs(context).toFloat()
        var vocal = if (LatencyStore.vocalFx(context)) 1f else 0f

        val knobs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Knob.labeled(context, "PITCH", -24f, 24f, pitch, { "%+.0fst".format(it) }) { pitch = it })
            addView(Knob.labeled(context, "MASTER", 0f, 127f, master, { "%.0f".format(it) }) { master = it })
            addView(Knob.labeled(context, "BUF", 1f, 4f, buffer, { "%.0fx".format(it) }) { buffer = it })
            addView(Knob.labeled(context, "LAT", 0f, 200f, latency, { "%.0fms".format(it) }) { latency = it })
            addView(Knob.labeled(context, "VOX", 0f, 1f, vocal, { if (it >= 0.5f) "on" else "off" }) { vocal = it })
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Project Sound")
            .setView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(HorizontalScrollView(context).apply { addView(knobs) })
                },
            )
            .setPositiveButton("Apply") { _, _ ->
                project.pitchSemitones = pitch.toInt()
                project.masterVolume = master.toInt()
                LatencyStore.setBufferMultiplier(context, buffer.toInt())
                LatencyStore.setRecordOffsetMs(context, latency.toInt())
                LatencyStore.setVocalFx(context, vocal >= 0.5f)
                Toast.makeText(context, "Project sound updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }
}
