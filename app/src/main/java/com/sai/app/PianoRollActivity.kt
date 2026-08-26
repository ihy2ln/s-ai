package com.sai.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.sai.core.tracker.NoteNames
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

/** A piano-roll editor for one phrase, mimicking FL Studio's layout: a note on the vertical axis
 *  (with the familiar black/white key row shading), the 16 steps on the horizontal axis. */
class PianoRollActivity : ComponentActivity() {

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private var phraseId: Int = 0
    private var instrumentId: Int? = null

    private lateinit var instrumentButton: Button
    private lateinit var rowsContainer: LinearLayout
    private val rowViews = mutableMapOf<Int, StepRowView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = TrackerProjectStore.get(this)
        library = SampleLibrary(this)

        phraseId = intent.getIntExtra(EXTRA_PHRASE_ID, -1)
        if (phraseId < 0) {
            finish()
            return
        }
        if (project.phrases[phraseId] == null) {
            project.putPhrase(phraseId, Phrase.empty())
        }

        val root = buildUi()
        setContentView(AppBackground.wrap(this, root))
        refreshRows()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "PIANO ROLL %02X".format(phraseId)
            setTextColor(AppTheme.accentColor(this@PianoRollActivity))
            typeface = Typeface.MONOSPACE
            textSize = 18f
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@PianoRollActivity, "N") { NavMenu.show(this@PianoRollActivity) })
        }

        instrumentButton = Button(this).apply {
            text = "-- pick instrument --"
            setOnClickListener { pickInstrument() }
        }

        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(titleRow)
            addView(instrumentButton)
            addView(
                ScrollView(this@PianoRollActivity).apply { addView(rowsContainer) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun pickInstrument() {
        val entries = library.all()
        if (entries.isEmpty()) {
            Toast.makeText(this, "Import a sample first (Menu > Samples or Sounds).", Toast.LENGTH_LONG).show()
            return
        }
        val labels = entries.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Instrument")
            .setItems(labels) { _, which ->
                instrumentId = entries[which].id
                instrumentButton.text = entries[which].displayName
            }
            .show()
    }

    private fun refreshRows() {
        rowsContainer.removeAllViews()
        rowViews.clear()
        for (note in HIGHEST_NOTE downTo LOWEST_NOTE) {
            rowsContainer.addView(noteRow(note))
        }
    }

    private fun noteRow(note: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val phrase = project.phrases[phraseId]
        val isBlackKey = note % 12 in setOf(1, 3, 6, 8, 10)

        val keyLabel = TextView(this).apply {
            text = NoteNames.format(note)
            setTextColor(if (isBlackKey) Color.rgb(140, 150, 160) else Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (22 * density).toInt())
        }

        val stepsRow = StepRowView(this).apply {
            stepCount = Phrase.STEP_COUNT
            setStates(BooleanArray(Phrase.STEP_COUNT) { phrase?.steps?.get(it)?.note == note })
            onStepToggleRequested = { index, desiredOn -> tryToggleNote(index, note, desiredOn) }
            layoutParams = LinearLayout.LayoutParams(0, (22 * density).toInt(), 1f)
        }
        rowViews[note] = stepsRow

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (isBlackKey) Color.rgb(26, 27, 31) else Color.rgb(20, 21, 24))
            addView(keyLabel)
            addView(stepsRow)
        }
    }

    /** A step holds only one note, so setting a note here must also clear it from whichever
     *  other row previously held that step - done by refreshing every row's own state in place
     *  (never rebuilding the view tree, which would break a StepRowView mid-drag). */
    private fun tryToggleNote(stepIndex: Int, note: Int, desiredOn: Boolean): Boolean {
        val instrument = instrumentId
        if (desiredOn && instrument == null) {
            Toast.makeText(this, "Pick an instrument first", Toast.LENGTH_SHORT).show()
            return false
        }
        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        steps[stepIndex] = if (desiredOn) Step(note = note, instrument = instrument, volume = steps[stepIndex].volume) else Step()
        val updated = Phrase(steps)
        project.putPhrase(phraseId, updated)

        for ((rowNote, rowView) in rowViews) {
            if (rowNote != note) {
                rowView.setStates(BooleanArray(Phrase.STEP_COUNT) { updated.steps[it].note == rowNote })
            }
        }
        return true
    }

    companion object {
        const val EXTRA_PHRASE_ID = "phrase_id"
        private const val LOWEST_NOTE = 48
        private const val HIGHEST_NOTE = 72
    }
}
