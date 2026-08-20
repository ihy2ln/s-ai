package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

/** The FL-Studio-style boolean step grid (one row per track/instrument, click-and-drag to paint
 *  a run of steps on or off, with a zoom control) as a reusable panel - used both inside
 *  [StepSequencerActivity] full-screen and as a Home-screen module. */
class StepSequencerPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val project = TrackerProjectStore.get(context)
    private val library = SampleLibrary(context)

    private val positionLabel: TextView
    private val zoomLabel: TextView
    private val rowsContainer: LinearLayout
    private val beatMarker: BeatMarkerView
    private val stepRowViews = mutableListOf<StepRowView>()
    private val instrumentColumnWidthPx: Int

    private var position = 0
    private var rowHeightDp = 36f
    private val rowInstrument = mutableMapOf<Int, Int>()

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        instrumentColumnWidthPx = (120 * density).toInt()

        val prevButton = Button(context).apply { text = "<"; setOnClickListener { movePosition(-1) } }
        val nextButton = Button(context).apply { text = ">"; setOnClickListener { movePosition(1) } }
        positionLabel = TextView(context).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams((60 * density).toInt(), LayoutParams.WRAP_CONTENT)
        }

        val zoomOutButton = Button(context).apply { text = "-"; setOnClickListener { changeZoom(-6f) } }
        val zoomInButton = Button(context).apply { text = "+"; setOnClickListener { changeZoom(6f) } }
        zoomLabel = TextView(context).apply {
            text = "Zoom"
            setTextColor(Color.rgb(140, 150, 160))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams((70 * density).toInt(), LayoutParams.WRAP_CONTENT)
        }

        val controlsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(prevButton)
            addView(positionLabel)
            addView(nextButton)
            addView(zoomLabel)
            addView(zoomOutButton)
            addView(zoomInButton)
        }

        beatMarker = BeatMarkerView(context).apply {
            stepCount = Phrase.STEP_COUNT
            layoutParams = LayoutParams(0, (10 * density).toInt(), 1f)
        }

        val beatHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context), LayoutParams(instrumentColumnWidthPx, LayoutParams.WRAP_CONTENT))
            addView(beatMarker)
        }

        rowsContainer = LinearLayout(context).apply { orientation = VERTICAL }

        addView(controlsRow)
        addView(beatHeaderRow)
        addView(
            ScrollView(context).apply { addView(rowsContainer) },
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )

        refreshRows()
    }

    fun setPlayhead(step: Int) {
        for (row in stepRowViews) row.playheadStep = step
    }

    fun refreshRows() {
        positionLabel.text = "%02X".format(position)
        zoomLabel.text = "Zoom %.0fdp".format(rowHeightDp)
        rowsContainer.removeAllViews()
        stepRowViews.clear()
        for (track in 0 until project.song.trackCount) {
            rowsContainer.addView(trackRow(track))
        }
    }

    private fun movePosition(delta: Int) {
        position = (position + delta).coerceIn(0, project.song.positions.size - 1)
        refreshRows()
    }

    private fun changeZoom(deltaDp: Float) {
        rowHeightDp = (rowHeightDp + deltaDp).coerceIn(28f, 56f)
        refreshRows()
    }

    private fun trackRow(track: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val rowHeightPx = (rowHeightDp * density).toInt()
        val phraseId = project.song.positions[position][track]
        val phrase = phraseId?.let { project.phrases[it] }

        val instrumentIndex = rowInstrument[track] ?: phrase?.steps?.firstNotNullOfOrNull { it.instrument }
        val instrumentLabel = instrumentIndex
            ?.let { library.all().getOrNull(it)?.displayName }
            ?: "Pick sample"

        val instrumentButton = compactRowButton(instrumentLabel, rowHeightPx).apply {
            layoutParams = LayoutParams(instrumentColumnWidthPx, rowHeightPx)
            setOnClickListener { pickInstrumentForRow(track) }
        }

        val stepRow = StepRowView(context).apply {
            stepCount = Phrase.STEP_COUNT
            setStates(BooleanArray(Phrase.STEP_COUNT) { phrase?.steps?.get(it)?.instrument != null })
            onStepToggleRequested = { index, desiredOn -> trySetStep(track, index, desiredOn) }
            layoutParams = LayoutParams(0, rowHeightPx, 1f)
        }
        stepRowViews.add(stepRow)

        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, rowHeightPx)
            addView(instrumentButton)
            addView(stepRow)
        }
    }

    private fun compactRowButton(label: String, heightPx: Int): Button {
        val density = resources.displayMetrics.density
        val vPad = (2 * density).toInt().coerceAtMost(heightPx / 4)
        return Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 10f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = 0
            minimumHeight = 0
            setPadding((6 * density).toInt(), vPad, (6 * density).toInt(), vPad)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, heightPx)
        }
    }

    private fun pickInstrumentForRow(track: Int) {
        val entries = library.all()
        if (entries.isEmpty()) {
            Toast.makeText(context, "Import a sample first (Menu > Samples or Sounds).", Toast.LENGTH_LONG).show()
            return
        }
        val labels = entries.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(context)
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
            Toast.makeText(context, "Pick an instrument for this row first", Toast.LENGTH_SHORT).show()
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
