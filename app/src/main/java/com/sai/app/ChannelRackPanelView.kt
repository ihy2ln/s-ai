package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

/** FL Studio-style Channel Rack & Step Sequencer panel (see Image-Line manual: Channel Rack). */
class ChannelRackPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val project = TrackerProjectStore.get(context)
    private val library = SampleLibrary(context)

    private val patternLabel: TextView
    private val rowsContainer: LinearLayout
    private val beatMarker: BeatMarkerView
    private val stepRowViews = mutableListOf<StepRowView>()
    private val rackRows = mutableListOf<ChannelRackRowView>()
    private val controlsWidthPx: Int

    private var pattern = 0
    private var rowHeightDp = 40f
    private var channels = ChannelRackStore.loadChannels(context)

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        controlsWidthPx = (162 * density).toInt()

        if (channels.size < ChannelRackStore.visibleCount(context)) {
            while (channels.size < ChannelRackStore.visibleCount(context)) {
                channels.add(RackChannelState())
            }
        }

        val title = TextView(context).apply {
            text = "CHANNEL RACK"
            setTextColor(AppTheme.accentColor(context))
            textSize = 11f
        }

        val prevPattern = compactButton("<") { movePattern(-1) }
        patternLabel = TextView(context).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 11f
            layoutParams = LayoutParams((72 * density).toInt(), LayoutParams.WRAP_CONTENT)
        }
        val nextPattern = compactButton(">") { movePattern(1) }

        val patternLengthLabel = TextView(context).apply {
            text = "${Phrase.STEP_COUNT} steps"
            setTextColor(Color.rgb(130, 140, 155))
            textSize = 10f
            setPadding((8 * density).toInt(), 0, 0, 0)
        }

        val optionsButton = compactButton("...") { showRackOptions() }
        val zoomOutButton = compactButton("-") { changeZoom(-4f) }
        val zoomInButton = compactButton("+") { changeZoom(4f) }

        val toolbarScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(prevPattern)
                    addView(patternLabel)
                    addView(nextPattern)
                    addView(patternLengthLabel)
                    addView(optionsButton)
                    addView(zoomOutButton)
                    addView(zoomInButton)
                },
            )
        }

        val toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, (6 * density).toInt(), 0)
            })
            addView(toolbarScroll)
        }

        beatMarker = BeatMarkerView(context).apply {
            stepCount = Phrase.STEP_COUNT
            layoutParams = LayoutParams(0, (8 * density).toInt(), 1f)
        }

        val beatHeaderRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = "Mute Vol Pan Trk"
                    setTextColor(Color.rgb(90, 100, 115))
                    textSize = 8f
                    layoutParams = LayoutParams(controlsWidthPx, LayoutParams.WRAP_CONTENT)
                },
            )
            addView(beatMarker)
        }

        rowsContainer = LinearLayout(context).apply { orientation = VERTICAL }

        val addChannelButton = Button(context).apply {
            text = "+ Add channel"
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            setOnClickListener { addChannel() }
        }

        addView(toolbar)
        addView(beatHeaderRow)
        addView(
            ScrollView(context).apply {
                isNestedScrollingEnabled = false
                addView(rowsContainer)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )
        addView(addChannelButton)

        refreshRows()
    }

    fun setPlayhead(step: Int) {
        for (row in stepRowViews) row.playheadStep = step
    }

    fun refreshRows() {
        patternLabel.text = "Pattern %02X".format(pattern)
        rowsContainer.removeAllViews()
        stepRowViews.clear()
        rackRows.clear()

        val visible = ChannelRackStore.visibleCount(context).coerceAtMost(channels.size)
        for (channelIndex in 0 until visible) {
            rowsContainer.addView(buildRow(channelIndex))
        }
        ChannelRackStore.saveChannels(context, channels)
    }

    private fun buildRow(channelIndex: Int): ChannelRackRowView {
        val density = resources.displayMetrics.density
        val rowHeightPx = (rowHeightDp * density).toInt()
        val state = channels.getOrElse(channelIndex) { RackChannelState() }
        val phraseId = project.song.positions.getOrNull(pattern)?.getOrNull(channelIndex)
        val phrase = phraseId?.let { project.phrases[it] }
        val instrumentIndex = state.instrumentIndex ?: phrase?.steps?.firstNotNullOfOrNull { it.instrument }
        if (instrumentIndex != null && state.instrumentIndex == null) {
            channels[channelIndex] = state.withInstrument(instrumentIndex)
        }

        val displayName = instrumentIndex
            ?.let { library.all().getOrNull(it)?.displayName }
            ?: "Empty"

        val row = ChannelRackRowView(context).apply {
            bind(channels[channelIndex], channelIndex, displayName, rowHeightPx)
            stepRow.setStates(BooleanArray(Phrase.STEP_COUNT) { phrase?.steps?.get(it)?.instrument != null })
            stepRow.onStepToggleRequested = { index, desiredOn -> trySetStep(channelIndex, index, desiredOn) }
            muteLed.onToggle = {
                updateChannel(channelIndex) { it.withMuted(!it.muted) }
            }
            volumeKnob.onChange = { value ->
                updateChannel(channelIndex) { it.withVolume(value) }
            }
            panKnob.onChange = { value ->
                updateChannel(channelIndex) { it.withPan(value) }
            }
            onMixerTrackClick = {
                val current = channels[channelIndex].mixerTrack
                updateChannel(channelIndex) { it.withMixerTrack((current + 1) % 10) }
                refreshRows()
            }
            onChannelClick = { pickInstrument(channelIndex) }
        }
        rackRows.add(row)
        stepRowViews.add(row.stepRow)
        return row
    }

    private fun updateChannel(index: Int, transform: (RackChannelState) -> RackChannelState) {
        if (index !in channels.indices) return
        channels[index] = transform(channels[index])
        ChannelRackStore.saveChannels(context, channels)
    }

    private fun movePattern(delta: Int) {
        pattern = (pattern + delta).coerceIn(0, project.song.positions.size - 1)
        refreshRows()
    }

    private fun changeZoom(deltaDp: Float) {
        rowHeightDp = (rowHeightDp + deltaDp).coerceIn(32f, 56f)
        refreshRows()
    }

    private fun addChannel() {
        val visible = ChannelRackStore.visibleCount(context)
        if (visible >= ChannelRackStore.MAX_CHANNELS) {
            Toast.makeText(context, "Maximum ${ChannelRackStore.MAX_CHANNELS} channels", Toast.LENGTH_SHORT).show()
            return
        }
        ChannelRackStore.setVisibleCount(context, visible + 1)
        if (channels.size <= visible) channels.add(RackChannelState())
        refreshRows()
    }

    private fun compactButton(label: String, onClick: () -> Unit): Button {
        val density = resources.displayMetrics.density
        return Button(context).apply {
            text = label
            textSize = 11f
            minHeight = 0
            minimumHeight = 0
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun showRackOptions() {
        AlertDialog.Builder(context)
            .setTitle("Channel Rack")
            .setItems(arrayOf("Mute all", "Unmute all", "Delete empty channels")) { _, which ->
                when (which) {
                    0 -> {
                        channels = channels.map { it.withMuted(true) }.toMutableList()
                        refreshRows()
                    }
                    1 -> {
                        channels = channels.map { it.withMuted(false) }.toMutableList()
                        refreshRows()
                    }
                    2 -> deleteEmptyChannels()
                }
            }
            .show()
    }

    private fun deleteEmptyChannels() {
        val visible = ChannelRackStore.visibleCount(context)
        if (visible <= ChannelRackStore.MIN_VISIBLE) {
            Toast.makeText(context, "At least ${ChannelRackStore.MIN_VISIBLE} channels remain", Toast.LENGTH_SHORT).show()
            return
        }
        val last = visible - 1
        val phraseId = project.song.positions.getOrNull(pattern)?.getOrNull(last)
        val phrase = phraseId?.let { project.phrases[it] }
        val hasSteps = phrase?.steps?.any { it.instrument != null } == true
        if (channels.getOrNull(last)?.instrumentIndex != null || hasSteps) {
            Toast.makeText(context, "Remove steps or sample from the last channel first", Toast.LENGTH_SHORT).show()
            return
        }
        ChannelRackStore.setVisibleCount(context, visible - 1)
        refreshRows()
    }

    private fun pickInstrument(channelIndex: Int) {
        val entries = library.all()
        if (entries.isEmpty()) {
            Toast.makeText(context, "Import a sample first (Menu > Samples or Sounds).", Toast.LENGTH_LONG).show()
            return
        }
        val labels = entries.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Channel ${channelIndex + 1}")
            .setItems(labels) { _, which ->
                updateChannel(channelIndex) { it.withInstrument(which) }
                refreshRows()
            }
            .show()
    }

    private fun trySetStep(channelIndex: Int, stepIndex: Int, on: Boolean): Boolean {
        val instrumentIndex = channels.getOrNull(channelIndex)?.instrumentIndex
        if (on && instrumentIndex == null) {
            Toast.makeText(context, "Assign a sample to this channel first", Toast.LENGTH_SHORT).show()
            return false
        }

        val existingPhraseId = project.song.positions[pattern][channelIndex]
        val phraseId = existingPhraseId ?: run {
            val id = project.nextPhraseId()
            project.putPhrase(id, Phrase.empty())
            project.setSongSlot(pattern, channelIndex, id)
            id
        }

        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        steps[stepIndex] = if (on) Step(instrument = instrumentIndex) else Step()
        project.putPhrase(phraseId, Phrase(steps))
        return true
    }
}
