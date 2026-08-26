package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.sai.core.tracker.Phrase

/** Shared N-menu: Home / Phrase / Piano Roll / Sample Editor / Sounds / Channel Rack / Manual. */
object NavMenu {

    fun show(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Navigate")
            .setItems(
                arrayOf("Home", "Phrase", "Piano Roll", "Sample Editor", "Sounds", "Channel Rack", "Manual"),
            ) { _, which ->
                when (which) {
                    0 -> goHome(context)
                    1 -> pickPhrase(context) { openPhrase(context, it) }
                    2 -> pickPhrase(context) { openPianoRoll(context, it) }
                    3 -> pickSample(context)
                    4 -> context.startActivity(Intent(context, SoundLibraryActivity::class.java))
                    5 -> context.startActivity(Intent(context, StepSequencerActivity::class.java))
                    6 -> context.startActivity(Intent(context, ManualActivity::class.java))
                }
            }
            .show()
    }

    fun goHome(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun openPhrase(context: Context, phraseId: Int) {
        context.startActivity(Intent(context, PhraseActivity::class.java).putExtra(PhraseActivity.EXTRA_PHRASE_ID, phraseId))
    }

    private fun openPianoRoll(context: Context, phraseId: Int) {
        context.startActivity(Intent(context, PianoRollActivity::class.java).putExtra(PianoRollActivity.EXTRA_PHRASE_ID, phraseId))
    }

    private fun pickPhrase(context: Context, onPicked: (Int) -> Unit) {
        val project = TrackerProjectStore.get(context)
        val ids = project.phrases.keys.sorted()
        if (ids.isEmpty()) {
            val id = project.nextPhraseId()
            project.putPhrase(id, Phrase.empty())
            onPicked(id)
            return
        }
        val labels = (ids.map { "Phrase %02X".format(it) } + "New Phrase").toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Phrase")
            .setItems(labels) { _, which ->
                if (which == ids.size) {
                    val id = project.nextPhraseId()
                    project.putPhrase(id, Phrase.empty())
                    onPicked(id)
                } else {
                    onPicked(ids[which])
                }
            }
            .show()
    }

    private fun pickSample(context: Context) {
        val entries = SampleLibrary(context).all()
        if (entries.isEmpty()) {
            Toast.makeText(context, "Import a sample first (Menu > Samples or Sounds).", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Sample Editor")
            .setItems(entries.map { it.displayName }.toTypedArray()) { _, which ->
                val entry = entries[which]
                context.startActivity(
                    Intent(context, SampleEditorActivity::class.java)
                        .putExtra(SampleEditorActivity.EXTRA_SAMPLE_URI, entry.uri)
                        .putExtra(SampleEditorActivity.EXTRA_SAMPLE_ID, entry.id),
                )
            }
            .show()
    }
}
