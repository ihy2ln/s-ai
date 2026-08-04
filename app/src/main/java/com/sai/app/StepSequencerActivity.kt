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
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

/** An FL-Studio-style boolean step grid: one row per track/instrument, 16 steps, tap to toggle a hit on or off. */
class StepSequencerActivity : ComponentActivity() {

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private lateinit var rootView: LinearLayout
    private lateinit var positionLabel: TextView
    private lateinit var rowsContainer: LinearLayout

    private var position = 0
    private val rowInstrument = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = TrackerProjectStore.get(this)
        library = SampleLibrary(this)

        setContentView(buildUi())
        AppBackground.apply(this, rootView)
        refreshRows()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "STEP SEQUENCER"
            setTextColor(Color.CYAN)
            typeface = Typeface.MONOSPACE
            textSize = 18f
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@StepSequencerActivity, "N") { onBackPressedDispatcher.onBackPressed() })
        }

        val prevButton = Button(this).apply { text = "<" ; setOnClickListener { movePosition(-1) } }
        val nextButton = Button(this).apply { text = ">" ; setOnClickListener { movePosition(1) } }
        positionLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((80 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val positionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(prevButton)
            addView(positionLabel)
            addView(nextButton)
        }

        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(titleRow)
            addView(positionRow)
            addView(
                ScrollView(this@StepSequencerActivity).apply { addView(rowsContainer) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        return rootView
    }

    private fun movePosition(delta: Int) {
        position = (position + delta).coerceIn(0, project.song.positions.size - 1)
        refreshRows()
    }

    private fun refreshRows() {
        positionLabel.text = "%02X".format(position)
        rowsContainer.removeAllViews()
        for (track in 0 until project.song.trackCount) {
            rowsContainer.addView(trackRow(track))
        }
    }

    private fun trackRow(track: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val phraseId = project.song.positions[position][track]
        val phrase = phraseId?.let { project.phrases[it] }

        val instrumentIndex = rowInstrument[track] ?: phrase?.steps?.firstNotNullOfOrNull { it.instrument }
        val instrumentLabel = instrumentIndex
            ?.let { library.all().getOrNull(it)?.displayName }
            ?: "-- pick sample --"

        val instrumentButton = Button(this).apply {
            text = instrumentLabel
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((150 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { pickInstrumentForRow(track) }
        }

        val stepsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (stepIndex in 0 until Phrase.STEP_COUNT) {
            val on = phrase?.steps?.get(stepIndex)?.instrument != null
            stepsRow.addView(stepCell(track, stepIndex, on), LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply {
                setMargins((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
            })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(instrumentButton)
            addView(stepsRow)
        }
    }

    private fun stepCell(track: Int, stepIndex: Int, on: Boolean): TextView {
        val groupShade = if ((stepIndex / 4) % 2 == 0) Color.rgb(40, 42, 48) else Color.rgb(30, 32, 36)
        return TextView(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(if (on) Color.rgb(255, 140, 40) else groupShade)
            setOnClickListener { toggleStep(track, stepIndex) }
        }
    }

    private fun pickInstrumentForRow(track: Int) {
        val entries = library.all()
        if (entries.isEmpty()) {
            Toast.makeText(this, "Import a sample first (Menu > Samples or Sounds).", Toast.LENGTH_LONG).show()
            return
        }
        val labels = entries.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Instrument for row ${track + 1}")
            .setItems(labels) { _, which ->
                rowInstrument[track] = which
                refreshRows()
            }
            .show()
    }

    private fun toggleStep(track: Int, stepIndex: Int) {
        val instrumentIndex = rowInstrument[track]
        val existingPhraseId = project.song.positions[position][track]
        val currentlyOn = existingPhraseId?.let { project.phrases[it]?.steps?.get(stepIndex)?.instrument != null } ?: false

        if (!currentlyOn && instrumentIndex == null) {
            Toast.makeText(this, "Pick an instrument for this row first", Toast.LENGTH_SHORT).show()
            return
        }

        val phraseId = existingPhraseId ?: run {
            val id = project.nextPhraseId()
            project.putPhrase(id, Phrase.empty())
            project.setSongSlot(position, track, id)
            id
        }

        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        steps[stepIndex] = if (currentlyOn) Step() else Step(instrument = instrumentIndex)
        project.putPhrase(phraseId, Phrase(steps))
        refreshRows()
    }
}
