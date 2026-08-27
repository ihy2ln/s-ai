package com.sai.app

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.sai.core.tracker.NoteNames
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

/** Piano roll: notes on Y, steps on X, with octave range, paint velocity, and note length. */
class PianoRollActivity : ComponentActivity() {

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private var phraseId: Int = 0
    private var instrumentId: Int? = null
    private var lowestNote = 48
    private var paintVelocity = 100
    private var paintLength = 0

    private lateinit var instrumentButton: Button
    private lateinit var rangeLabel: TextView
    private lateinit var velocityButton: Button
    private lateinit var lengthButton: Button
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

        val title = Ui.screenTitle(this, "PIANO ROLL %02X".format(phraseId))
        val titleRow = Ui.headerBar(this) {
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@PianoRollActivity, "N") { NavMenu.show(this@PianoRollActivity) })
        }

        instrumentButton = Ui.compactButton(this, "-- pick instrument --") { pickInstrument() }

        rangeLabel = TextView(this).apply {
            setTextColor(AppTheme.textPrimary)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        velocityButton = Ui.compactButton(this, "Vel") { cycleVelocity() }
        lengthButton = Ui.compactButton(this, "Len") { cycleLength() }
        refreshToolbarLabels()

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.compactButton(this@PianoRollActivity, "-8va") { shiftRange(-12) })
            addView(rangeLabel)
            addView(Ui.compactButton(this@PianoRollActivity, "+8va") { shiftRange(12) })
            addView(velocityButton)
            addView(lengthButton)
        }

        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(AppTheme.canvas)
            addView(titleRow)
            addView(instrumentButton)
            addView(HorizontalScrollView(this@PianoRollActivity).apply { addView(tools) })
            addView(
                ScrollView(this@PianoRollActivity).apply { addView(rowsContainer) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun refreshToolbarLabels() {
        rangeLabel.text = " ${NoteNames.format(lowestNote)}–${NoteNames.format(highestNote())} "
        velocityButton.text = "Vel $paintVelocity"
        lengthButton.text = if (paintLength <= 0) "Len full" else "Len $paintLength"
    }

    private fun highestNote() = (lowestNote + RANGE_SEMITONES - 1).coerceAtMost(127)

    private fun shiftRange(delta: Int) {
        lowestNote = (lowestNote + delta).coerceIn(0, 127 - RANGE_SEMITONES + 1)
        refreshToolbarLabels()
        refreshRows()
    }

    private fun cycleVelocity() {
        paintVelocity = when (paintVelocity) {
            127 -> 50
            50 -> 80
            80 -> 100
            else -> 127
        }
        refreshToolbarLabels()
    }

    private fun cycleLength() {
        paintLength = when (paintLength) {
            0 -> 1
            1 -> 2
            2 -> 4
            4 -> 8
            else -> 0
        }
        refreshToolbarLabels()
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
        for (note in highestNote() downTo lowestNote) {
            rowsContainer.addView(noteRow(note))
        }
    }

    private fun noteRow(note: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val phrase = project.phrases[phraseId]
        val isBlackKey = note % 12 in setOf(1, 3, 6, 8, 10)

        val keyLabel = TextView(this).apply {
            text = NoteNames.format(note)
            setTextColor(if (isBlackKey) AppTheme.textSecondary else AppTheme.textPrimary)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (22 * density).toInt())
        }

        val stepsRow = StepRowView(this).apply {
            stepCount = Phrase.MAX_STEPS
            setStates(BooleanArray(Phrase.MAX_STEPS) { covers(phrase, it, note) })
            onStepToggleRequested = { index, desiredOn -> tryToggleNote(index, note, desiredOn) }
            layoutParams = LinearLayout.LayoutParams(0, (22 * density).toInt(), 1f)
        }
        rowViews[note] = stepsRow

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (isBlackKey) AppTheme.pianoBlack else AppTheme.pianoWhite)
            addView(keyLabel)
            addView(stepsRow)
        }
    }

    private fun covers(phrase: Phrase?, stepIndex: Int, note: Int): Boolean {
        val steps = phrase?.steps ?: return false
        val here = steps.getOrNull(stepIndex) ?: return false
        if (here.note == note) return true
        for (start in 0 until stepIndex) {
            val step = steps[start]
            if (step.note != note) continue
            val span = (step.length ?: 1).coerceAtLeast(1)
            if (stepIndex < start + span) return true
        }
        return false
    }

    private fun tryToggleNote(stepIndex: Int, note: Int, desiredOn: Boolean): Boolean {
        val instrument = instrumentId
        if (desiredOn && instrument == null) {
            Toast.makeText(this, "Pick an instrument first", Toast.LENGTH_SHORT).show()
            return false
        }
        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        steps[stepIndex] = if (desiredOn) {
            Step(
                note = note,
                instrument = instrument,
                volume = paintVelocity,
                length = paintLength.takeIf { it > 0 },
            )
        } else {
            Step()
        }
        val updated = Phrase(steps)
        project.putPhrase(phraseId, updated)

        for ((rowNote, rowView) in rowViews) {
            rowView.setStates(BooleanArray(Phrase.MAX_STEPS) { covers(updated, it, rowNote) })
        }
        return true
    }

    companion object {
        const val EXTRA_PHRASE_ID = "phrase_id"
        private const val RANGE_SEMITONES = 24
    }
}
