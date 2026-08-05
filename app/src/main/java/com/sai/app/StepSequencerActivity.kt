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

/** An FL-Studio-style boolean step grid: one row per track/instrument, tap-and-drag across
 *  steps to paint a run of hits on or off, with a zoom control to fit more rows on screen. */
class StepSequencerActivity : ComponentActivity() {

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private lateinit var rootView: LinearLayout
    private lateinit var positionLabel: TextView
    private lateinit var zoomLabel: TextView
    private lateinit var rowsContainer: LinearLayout

    private var position = 0
    private var rowHeightDp = 32f
    private val rowInstrument = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = TrackerProjectStore.get(this)
        library = SampleLibrary(this)

        setContentView(AppBackground.wrap(this, buildUi()))
        refreshRows()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "STEP SEQUENCER"
            setTextColor(AppTheme.accentColor(this@StepSequencerActivity))
            typeface = Typeface.MONOSPACE
            textSize = 18f
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@StepSequencerActivity, "N") { onBackPressedDispatcher.onBackPressed() })
        }

        val prevButton = Button(this).apply { text = "<"; setOnClickListener { movePosition(-1) } }
        val nextButton = Button(this).apply { text = ">"; setOnClickListener { movePosition(1) } }
        positionLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((60 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val zoomOutButton = Button(this).apply { text = "-"; setOnClickListener { changeZoom(-6f) } }
        val zoomInButton = Button(this).apply { text = "+"; setOnClickListener { changeZoom(6f) } }
        zoomLabel = TextView(this).apply {
            text = "Zoom"
            setTextColor(Color.rgb(140, 150, 160))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((70 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(prevButton)
            addView(positionLabel)
            addView(nextButton)
            addView(zoomLabel)
            addView(zoomOutButton)
            addView(zoomInButton)
        }

        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(titleRow)
            addView(controlsRow)
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

    private fun changeZoom(deltaDp: Float) {
        rowHeightDp = (rowHeightDp + deltaDp).coerceIn(20f, 56f)
        refreshRows()
    }

    private fun refreshRows() {
        positionLabel.text = "%02X".format(position)
        zoomLabel.text = "Zoom %.0fdp".format(rowHeightDp)
        rowsContainer.removeAllViews()
        for (track in 0 until project.song.trackCount) {
            rowsContainer.addView(trackRow(track))
        }
    }

    private fun trackRow(track: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val rowHeightPx = (rowHeightDp * density).toInt()
        val phraseId = project.song.positions[position][track]
        val phrase = phraseId?.let { project.phrases[it] }

        val instrumentIndex = rowInstrument[track] ?: phrase?.steps?.firstNotNullOfOrNull { it.instrument }
        val instrumentLabel = instrumentIndex
            ?.let { library.all().getOrNull(it)?.displayName }
            ?: "-- pick sample --"

        val instrumentButton = Button(this).apply {
            text = instrumentLabel
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((150 * density).toInt(), rowHeightPx)
            setOnClickListener { pickInstrumentForRow(track) }
        }

        val stepRow = StepRowView(this).apply {
            stepCount = Phrase.STEP_COUNT
            setStates(BooleanArray(Phrase.STEP_COUNT) { phrase?.steps?.get(it)?.instrument != null })
            onStepToggleRequested = { index, desiredOn -> trySetStep(track, index, desiredOn) }
            layoutParams = LinearLayout.LayoutParams(0, rowHeightPx, 1f)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(instrumentButton)
            addView(stepRow)
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

    private fun trySetStep(track: Int, stepIndex: Int, on: Boolean): Boolean {
        val instrumentIndex = rowInstrument[track]
        if (on && instrumentIndex == null) {
            Toast.makeText(this, "Pick an instrument for this row first", Toast.LENGTH_SHORT).show()
            return false
        }

        val existingPhraseId = project.song.positions[position][track]
        val phraseId = existingPhraseId ?: run {
            val id = project.nextPhraseId()
            project.putPhrase(id, Phrase.empty())
            project.setSongSlot(position, track, id)
            id
        }

        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        steps[stepIndex] = if (on) Step(instrument = instrumentIndex) else Step()
        project.putPhrase(phraseId, Phrase(steps))
        return true
    }
}
