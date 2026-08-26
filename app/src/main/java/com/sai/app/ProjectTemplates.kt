package com.sai.app

import android.app.AlertDialog
import android.content.Context
import com.sai.core.tracker.Phrase

object ProjectTemplates {

    enum class Kind { EMPTY, STEPS_16, STEPS_32, BEAT }

    fun show(context: Context, onApplied: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("New project")
            .setItems(arrayOf("Empty", "16-step grid", "32-step grid", "Beat (phrase on 00)")) { _, which ->
                val kind = when (which) {
                    1 -> Kind.STEPS_16
                    2 -> Kind.STEPS_32
                    3 -> Kind.BEAT
                    else -> Kind.EMPTY
                }
                apply(context, kind)
                onApplied()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun apply(context: Context, kind: Kind) {
        val project = TrackerProjectStore.get(context)
        project.resetProject()
        PlaylistStore.clear(context)
        when (kind) {
            Kind.EMPTY -> Unit
            Kind.STEPS_16 -> project.setAllPatternLengths(16)
            Kind.STEPS_32 -> project.setAllPatternLengths(32)
            Kind.BEAT -> {
                project.setAllPatternLengths(16)
                val id = project.nextPhraseId()
                project.putPhrase(id, Phrase.empty())
                project.setSongSlot(0, 0, id)
            }
        }
    }
}
