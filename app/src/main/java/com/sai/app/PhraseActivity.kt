package com.sai.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.sai.core.tracker.NoteNames
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

class PhraseActivity : ComponentActivity() {

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private var phraseId: Int = 0
    private lateinit var rows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = TrackerProject(this)
        library = SampleLibrary(this)
        phraseId = intent.getIntExtra(EXTRA_PHRASE_ID, -1)
        if (phraseId < 0) {
            finish()
            return
        }
        if (project.phrases[phraseId] == null) {
            project.putPhrase(phraseId, Phrase.empty())
        }

        setContentView(buildUi())
        refresh()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "PHRASE %02X".format(phraseId)
            setTextColor(Color.CYAN)
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }

        val header = gridRow(listOf("  ", "NOTE", "INS", "VOL"), Color.rgb(120, 140, 160))

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(title)
            addView(header)
            addView(
                ScrollView(this@PhraseActivity).apply { addView(rows) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun refresh() {
        rows.removeAllViews()
        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        for (stepIndex in phrase.steps.indices) {
            val step = phrase.steps[stepIndex]
            rows.addView(stepRow(stepIndex, step))
        }
    }

    private fun stepRow(stepIndex: Int, step: Step): LinearLayout {
        val density = resources.displayMetrics.density
        val label = "%02X".format(stepIndex)
        val noteText = step.note?.let(NoteNames::format) ?: "---"
        val instrText = step.instrument?.let { "%02X".format(it) } ?: "--"
        val volText = step.volume?.let { "%03d".format(it) } ?: "---"

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cellLabel(label))
            addView(cellValue(noteText) { editNote(stepIndex) }, cellParams(density, 60))
            addView(cellValue(instrText) { editInstrument(stepIndex) }, cellParams(density, 44))
            addView(cellValue(volText) { editVolume(stepIndex) }, cellParams(density, 48))
        }
    }

    private fun cellLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.rgb(90, 110, 130))
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(8, 8, 8, 8)
    }

    private fun cellValue(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(8, 8, 8, 8)
        setOnClickListener { onClick() }
    }

    private fun cellParams(density: Float, widthDp: Int) =
        LinearLayout.LayoutParams((widthDp * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun gridRow(labels: List<String>, color: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val widths = listOf(28, 60, 44, 48)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for ((index, text) in labels.withIndex()) {
                addView(
                    TextView(this@PhraseActivity).apply {
                        this.text = text
                        setTextColor(color)
                        typeface = Typeface.MONOSPACE
                        gravity = Gravity.CENTER
                        setPadding(8, 4, 8, 4)
                    },
                    LinearLayout.LayoutParams((widths[index] * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT),
                )
            }
        }
    }

    private fun editNote(stepIndex: Int) {
        val phrase = project.phrases[phraseId] ?: return
        val current = phrase.steps[stepIndex].note
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current?.toString().orEmpty())
            hint = "0-127, blank to clear"
        }
        AlertDialog.Builder(this)
            .setTitle("Note (step %02X)".format(stepIndex))
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(0, 127)
                updateStep(stepIndex) { it.copy(note = value) }
            }
            .setNeutralButton("Clear") { _, _ -> updateStep(stepIndex) { it.copy(note = null) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editVolume(stepIndex: Int) {
        val phrase = project.phrases[phraseId] ?: return
        val current = phrase.steps[stepIndex].volume
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current?.toString().orEmpty())
            hint = "0-127, blank to clear"
        }
        AlertDialog.Builder(this)
            .setTitle("Volume (step %02X)".format(stepIndex))
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(0, 127)
                updateStep(stepIndex) { it.copy(volume = value) }
            }
            .setNeutralButton("Clear") { _, _ -> updateStep(stepIndex) { it.copy(volume = null) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editInstrument(stepIndex: Int) {
        val entries = library.all()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No samples imported yet")
                .setMessage("Import samples from the main screen first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = (entries.indices.map { "%02X  ".format(it) + entries[it].displayName } + "Clear").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Instrument (step %02X)".format(stepIndex))
            .setItems(labels) { _, which ->
                if (which == entries.size) {
                    updateStep(stepIndex) { it.copy(instrument = null) }
                } else {
                    updateStep(stepIndex) { it.copy(instrument = which) }
                }
            }
            .show()
    }

    private fun updateStep(stepIndex: Int, transform: (Step) -> Step) {
        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        steps[stepIndex] = transform(steps[stepIndex])
        project.putPhrase(phraseId, Phrase(steps))
        refresh()
    }

    companion object {
        const val EXTRA_PHRASE_ID = "phrase_id"
    }
}
