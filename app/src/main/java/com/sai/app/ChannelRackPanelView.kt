package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.sai.core.tracker.LoopMode
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

/** Channel Rack & Step Sequencer panel: mute/solo, length, swing, loop, duplicate. */
class ChannelRackPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val project = TrackerProjectStore.get(context)
    private val library = SampleLibrary(context)

    private val patternLabel: TextView
    private val patternLengthButton: Button
    private val swingButton: Button
    private val loopButton: Button
    private val rowsContainer: LinearLayout
    private val stepRowViews = mutableListOf<StepRowView>()
    private val rackRows = mutableListOf<ChannelRackRowView>()
    private val controlsWidthPx: Int

    private var pattern = 0
    private var rowHeightDp = 40f
    private var channels = ChannelRackStore.loadChannels(context)

    val currentPattern: Int get() = pattern

    var onSongChanged: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        controlsWidthPx = (188 * density).toInt()

        if (channels.size < ChannelRackStore.visibleCount(context)) {
            while (channels.size < ChannelRackStore.visibleCount(context)) {
                channels.add(RackChannelState())
            }
        }

        val title = TextView(context).apply {
            text = "CHANNEL RACK"
            setTextColor(AppTheme.accentColor(context))
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
        }

        val prevPattern = Ui.compactButton(context, "<") { movePattern(-1) }
        patternLabel = TextView(context).apply {
            setTextColor(AppTheme.textPrimary)
            gravity = Gravity.CENTER
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LayoutParams((72 * density).toInt(), LayoutParams.WRAP_CONTENT)
        }
        val nextPattern = Ui.compactButton(context, ">") { movePattern(1) }

        patternLengthButton = Ui.compactButton(context, lengthLabel()) { cyclePatternLength() }
        swingButton = Ui.compactButton(context, swingLabel()) { editSwing() }
        loopButton = Ui.compactButton(context, loopLabel()) { cycleLoopMode() }
        val optionsButton = Ui.compactButton(context, "···") { showRackOptions() }
        val zoomOutButton = Ui.compactButton(context, "−") { changeZoom(-4f) }
        val zoomInButton = Ui.compactButton(context, "+") { changeZoom(4f) }

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
                    addView(patternLengthButton)
                    addView(swingButton)
                    addView(loopButton)
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

        val labelRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = "Mute Solo Vol Pan Trk"
                    setTextColor(AppTheme.textMuted)
                    textSize = 8f
                    layoutParams = LayoutParams(controlsWidthPx, LayoutParams.WRAP_CONTENT)
                },
            )
        }

        rowsContainer = LinearLayout(context).apply { orientation = VERTICAL }

        val addChannelButton = Ui.compactButton(context, "+ Add channel") { addChannel() }

        addView(toolbar)
        addView(labelRow)
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

    fun syncFromStore() {
        channels = ChannelRackStore.loadChannels(context)
        refreshRows()
    }

    fun refreshRows() {
        patternLabel.text = "Pattern %02X".format(pattern)
        patternLengthButton.text = lengthLabel()
        swingButton.text = swingLabel()
        loopButton.text = loopLabel()
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
        val instrumentId = state.instrumentId ?: phrase?.steps?.firstNotNullOfOrNull { it.instrument }
        if (instrumentId != null && state.instrumentId == null) {
            channels[channelIndex] = state.withInstrument(instrumentId)
        }

        val displayName = instrumentId
            ?.let { library.get(it)?.displayName }
            ?: "Empty"
        val length = project.patternLength(pattern)

        val row = ChannelRackRowView(context).apply {
            bind(channels[channelIndex], channelIndex, displayName, rowHeightPx, length)
            stepRow.setStates(BooleanArray(length) { phrase?.steps?.get(it)?.instrument != null })
            stepRow.onStepToggleRequested = { index, desiredOn -> trySetStep(channelIndex, index, desiredOn) }
            muteLed.onToggle = {
                val next = !channels[channelIndex].muted
                updateChannel(channelIndex) { it.withMuted(next) }
                muteLed.muted = next
            }
            soloLed.onToggle = {
                val next = !channels[channelIndex].soloed
                updateChannel(channelIndex) { it.withSoloed(next) }
                soloLed.muted = !next
            }
            volumeKnob.onChange = { value ->
                updateChannel(channelIndex) { it.withVolume(value) }
            }
            panKnob.onChange = { value ->
                updateChannel(channelIndex) { it.withPan(value) }
            }
            onMixerTrackClick = {
                val current = channels[channelIndex].mixerTrack
                updateChannel(channelIndex) { it.withMixerTrack((current + 1) % (MixerStore.STRIP_COUNT + 1)) }
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

    private fun lengthLabel(): String = "${project.patternLength(pattern)} steps"

    private fun swingLabel(): String = "Swing ${project.swing}"

    private fun loopLabel(): String = when (project.loopMode) {
        LoopMode.SONG -> "Loop song"
        LoopMode.PATTERN -> "Loop pat"
        LoopMode.RANGE -> "Loop %02X–%02X".format(project.loopStart, project.loopEnd)
    }

    private fun cyclePatternLength() {
        val current = project.patternLength(pattern)
        val next = Phrase.LENGTHS[(Phrase.LENGTHS.indexOf(current) + 1) % Phrase.LENGTHS.size]
        project.setPatternLength(pattern, next)
        refreshRows()
        onSongChanged?.invoke()
        Toast.makeText(context, "Pattern ${"%02X".format(pattern)} is $next steps", Toast.LENGTH_SHORT).show()
    }

    private fun editSwing() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(project.swing.toString())
            hint = "0–100"
        }
        AlertDialog.Builder(context)
            .setTitle("Swing")
            .setMessage("0 is straight 16ths. 50 is a light shuffle. 100 delays offbeats halfway to the next even step.")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                project.swing = input.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 0
                refreshRows()
                onSongChanged?.invoke()
            }
            .setNeutralButton("0") { _, _ ->
                project.swing = 0
                refreshRows()
                onSongChanged?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cycleLoopMode() {
        project.loopMode = when (project.loopMode) {
            LoopMode.SONG -> {
                project.loopStart = pattern
                project.loopEnd = pattern
                LoopMode.PATTERN
            }
            LoopMode.PATTERN -> LoopMode.RANGE
            LoopMode.RANGE -> LoopMode.SONG
        }
        refreshRows()
        onSongChanged?.invoke()
        Toast.makeText(context, loopToast(), Toast.LENGTH_SHORT).show()
    }

    private fun loopToast(): String = when (project.loopMode) {
        LoopMode.SONG -> "Looping the whole song"
        LoopMode.PATTERN -> "Looping pattern %02X".format(pattern)
        LoopMode.RANGE -> "Looping rows %02X–%02X (long-press a tracker row to change)".format(project.loopStart, project.loopEnd)
    }

    private fun showRackOptions() {
        AlertDialog.Builder(context)
            .setTitle("Channel Rack")
            .setItems(
                arrayOf(
                    "Duplicate pattern",
                    "Mute all",
                    "Unmute all",
                    "Solo none",
                    "Delete empty channels",
                ),
            ) { _, which ->
                when (which) {
                    0 -> duplicatePattern()
                    1 -> {
                        channels = channels.map { it.withMuted(true) }.toMutableList()
                        refreshRows()
                    }
                    2 -> {
                        channels = channels.map { it.withMuted(false) }.toMutableList()
                        refreshRows()
                    }
                    3 -> {
                        channels = channels.map { it.withSoloed(false) }.toMutableList()
                        refreshRows()
                    }
                    4 -> deleteEmptyChannels()
                }
            }
            .show()
    }

    private fun duplicatePattern() {
        val dest = project.duplicatePattern(pattern) ?: return
        pattern = dest
        refreshRows()
        onSongChanged?.invoke()
        Toast.makeText(context, "Copied to pattern %02X".format(dest), Toast.LENGTH_SHORT).show()
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
        if (channels.getOrNull(last)?.instrumentId != null || hasSteps) {
            Toast.makeText(context, "Remove steps or sample from the last channel first", Toast.LENGTH_SHORT).show()
            return
        }
        ChannelRackStore.setVisibleCount(context, visible - 1)
        refreshRows()
    }

    /** Asks which channel should host [instrumentId], then assigns it there. */
    fun promptAssignInstrument(instrumentId: Int, displayName: String) {
        val visible = ChannelRackStore.visibleCount(context).coerceAtMost(channels.size)
        if (visible <= 0) return
        val labels = (0 until visible).map { index ->
            val current = channels[index].instrumentId
                ?.let { library.get(it)?.displayName }
                ?: "Empty"
            "Channel ${index + 1} - $current"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Place $displayName in")
            .setItems(labels) { _, which ->
                updateChannel(which) { it.withInstrument(instrumentId) }
                refreshRows()
                Toast.makeText(context, "$displayName on channel ${which + 1}", Toast.LENGTH_SHORT).show()
            }
            .show()
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
                updateChannel(channelIndex) { it.withInstrument(entries[which].id) }
                refreshRows()
            }
            .show()
    }

    private fun trySetStep(channelIndex: Int, stepIndex: Int, on: Boolean): Boolean {
        val length = project.patternLength(pattern)
        if (stepIndex !in 0 until length) return false
        val instrumentId = channels.getOrNull(channelIndex)?.instrumentId
        if (on && instrumentId == null) {
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
        steps[stepIndex] = if (on) Step(instrument = instrumentId) else Step()
        project.putPhrase(phraseId, Phrase.fromSteps(steps))
        return true
    }
}
