package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.SampleWarp
import com.sai.core.audio.Wav

class SamplerPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val sampleNameLabel: TextView
    private val waveform: WaveformView
    private val sliceCountLabel: TextView
    private val padContainer: LinearLayout

    private var wav: Wav? = null
    private var sourceName: String = ""
    private var sliceCount = 8

    var onSaveSlices: ((sourceName: String, slices: List<Wav>) -> Unit)? = null
    var onSendToRack: ((sourceName: String, slices: List<Wav>) -> Unit)? = null

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density

        sampleNameLabel = TextView(context).apply {
            text = "No sample loaded"
            setTextColor(AppTheme.textPrimary)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        waveform = WaveformView(context)

        sliceCountLabel = TextView(context).apply { setTextColor(AppTheme.textPrimary) }
        val minusButton = Ui.compactButton(context, "−") { changeSliceCount(-1) }
        val plusButton = Ui.compactButton(context, "+") { changeSliceCount(1) }
        val saveButton = Ui.compactButton(context, "Save Slices") { saveSlices() }
        val toRackButton = Ui.compactButton(context, "To Rack") { sendSlicesToRack() }
        val warpButton = Ui.compactButton(context, "Warp BPM") { warpToProjectBpm() }
        val controlsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(minusButton)
            addView(sliceCountLabel)
            addView(plusButton)
            addView(saveButton)
            addView(toRackButton)
            addView(warpButton)
        }

        padContainer = LinearLayout(context).apply { orientation = VERTICAL }

        addView(sampleNameLabel)
        addView(waveform, LayoutParams(LayoutParams.MATCH_PARENT, (110 * density).toInt()))
        addView(controlsRow)
        addView(padContainer)

        refresh()
    }

    fun load(wav: Wav, name: String) {
        this.wav = wav
        this.sourceName = name.substringBeforeLast('.')
        sampleNameLabel.text = name
        refresh()
    }

    fun currentWav(): Wav? = wav

    fun currentSourceName(): String = sourceName

    private fun changeSliceCount(delta: Int) {
        sliceCount = (sliceCount + delta).coerceIn(1, 16)
        refresh()
    }

    private fun sliceBounds(wav: Wav): List<IntRange> {
        val frameCount = wav.frameCount
        return (0 until sliceCount).map { i ->
            val start = i * frameCount / sliceCount
            val end = if (i == sliceCount - 1) frameCount else (i + 1) * frameCount / sliceCount
            start until end
        }
    }

    private fun refresh() {
        sliceCountLabel.text = " %d ".format(sliceCount)
        padContainer.removeAllViews()

        val currentWav = wav
        if (currentWav == null) {
            waveform.channels = 1
            waveform.samples = ShortArray(0)
            return
        }

        val bounds = sliceBounds(currentWav)
        waveform.channels = currentWav.channels
        waveform.samples = currentWav.samples
        waveform.sliceBoundaries = bounds.drop(1).map { it.first }

        val columns = 4
        var row: LinearLayout? = null
        for ((index, range) in bounds.withIndex()) {
            if (index % columns == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                padContainer.addView(row)
            }
            val pad = Button(context).apply {
                text = "%02X".format(index)
                setBackgroundColor(PALETTE[index % PALETTE.size])
                setTextColor(Color.BLACK)
                setOnClickListener { previewSlice(currentWav, range) }
            }
            row!!.addView(pad, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun previewSlice(wav: Wav, range: IntRange) {
        val choke = ModuleLayoutStore.isChokeEnabled(context, ModuleType.SAMPLER)
        AudioPlayback.playOneShot(
            SampleEditor.trim(wav, range.first, range.last + 1),
            context = context,
            chokeGroup = if (choke) "sampler" else null,
        )
    }

    private fun saveSlices() {
        val currentWav = wav ?: return
        val bounds = sliceBounds(currentWav)
        val slices = bounds.map { range -> SampleEditor.trim(currentWav, range.first, range.last + 1) }
        onSaveSlices?.invoke(sourceName, slices)
    }

    private fun sendSlicesToRack() {
        val currentWav = wav ?: return
        val bounds = sliceBounds(currentWav)
        val slices = bounds.map { range -> SampleEditor.trim(currentWav, range.first, range.last + 1) }
        onSendToRack?.invoke(sourceName, slices)
    }

    private fun warpToProjectBpm() {
        val current = wav
        if (current == null) {
            Toast.makeText(context, "Load a sample first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Source BPM"
        }
        AlertDialog.Builder(context)
            .setTitle("Warp to project BPM")
            .setMessage("Enter the sample's original tempo. S.Ai time-stretches it to the project BPM (${TrackerProjectStore.get(context).bpm}).")
            .setView(input)
            .setPositiveButton("Warp") { _, _ ->
                val sourceBpm = input.text.toString().toDoubleOrNull()
                if (sourceBpm == null || sourceBpm <= 0.0) {
                    Toast.makeText(context, "Enter a source BPM", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val target = TrackerProjectStore.get(context).bpm.toDouble()
                wav = SampleWarp.bpmSync(current, sourceBpm, target)
                refresh()
                Toast.makeText(context, "Warped ${sourceBpm.toInt()} → ${target.toInt()} BPM", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
            Color.rgb(3, 169, 244), Color.rgb(139, 195, 74),
        )
    }
}
