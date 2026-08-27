package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.sai.core.tracker.Phrase

/** Shared N-menu: Home / Phrase / Piano Roll / Sample Editor / Sounds / Channel Rack / Mixer / Playlist / Manual. */
object NavMenu {

    private data class Destination(val title: String, val subtitle: String, val action: (Context) -> Unit)

    fun show(context: Context) {
        val destinations = listOf(
            Destination("Home", "Workspace modules", ::goHome),
            Destination("Phrase", "Hex step editor") { pickPhrase(it) { id -> openPhrase(it, id) } },
            Destination("Piano Roll", "Notes on a grid") { pickPhrase(it) { id -> openPianoRoll(it, id) } },
            Destination("Sample Editor", "Trim, warp, pitch") { pickSample(it) },
            Destination("Sounds", "Library, tags, categories") { it.startActivity(Intent(it, SoundLibraryActivity::class.java)) },
            Destination("Channel Rack", "FL-style step sequencer") { it.startActivity(Intent(it, StepSequencerActivity::class.java)) },
            Destination("Mixer", "Faders, mute, live insert FX") { it.startActivity(Intent(it, MixerActivity::class.java)) },
            Destination("Playlist", "Arrange patterns and tape") { it.startActivity(Intent(it, PlaylistActivity::class.java)) },
            Destination("Manual", "Built-in wiki") { it.startActivity(Intent(it, ManualActivity::class.java)) },
        )
        val dialog = AlertDialog.Builder(context, R.style.Theme_Sai_Dialog)
            .setTitle("Navigate")
            .setNegativeButton("Close", null)
            .create()
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(context, 8f)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(AppTheme.surface)
            destinations.forEach { dest ->
                addView(
                    Ui.listRow(
                        context = context,
                        title = dest.title,
                        subtitle = dest.subtitle,
                        leading = AppTheme.accentColor(context),
                        trailing = "›",
                        onClick = {
                            dialog.dismiss()
                            dest.action(context)
                        },
                    ),
                )
            }
        }
        dialog.setView(ScrollView(context).apply { addView(list) })
        dialog.show()
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
