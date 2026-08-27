package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.sai.core.tracker.Phrase

/** Shared N-menu: Home / Phrase / Piano Roll / Sample Editor / Sounds / Channel Rack / Mixer / Playlist / Guide / Manual. */
object NavMenu {

    fun show(context: Context) {
        val items = arrayOf(
            "Home",
            "Phrase",
            "Piano Roll",
            "Sample Editor",
            "Sounds",
            "Channel Rack",
            "Mixer",
            "Playlist",
            "Guide",
            "Manual",
        )
        AlertDialog.Builder(context)
            .setTitle("Navigate")
            .setItems(items) { _, which ->
                when (items[which]) {
                    "Home" -> goHome(context)
                    "Phrase" -> pickPhrase(context) { openPhrase(context, it) }
                    "Piano Roll" -> pickPhrase(context) { openPianoRoll(context, it) }
                    "Sample Editor" -> pickSample(context)
                    "Sounds" -> context.startActivity(Intent(context, SoundLibraryActivity::class.java))
                    "Channel Rack" -> context.startActivity(Intent(context, StepSequencerActivity::class.java))
                    "Mixer" -> context.startActivity(Intent(context, MixerActivity::class.java))
                    "Playlist" -> context.startActivity(Intent(context, PlaylistActivity::class.java))
                    "Guide" -> GuideActivity.open(context)
                    "Manual" -> context.startActivity(Intent(context, ManualActivity::class.java))
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
