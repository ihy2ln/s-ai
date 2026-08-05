package com.sai.app

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sai.core.audio.SampleEditor
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

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density

        sampleNameLabel = TextView(context).apply {
            text = "No sample loaded"
            setTextColor(Color.WHITE)
        }

        waveform = WaveformView(context)

        sliceCountLabel = TextView(context).apply { setTextColor(Color.WHITE) }
        val minusButton = Button(context).apply {
            text = "-"
            setOnClickListener { changeSliceCount(-1) }
        }
        val plusButton = Button(context).apply {
            text = "+"
            setOnClickListener { changeSliceCount(1) }
        }
        val saveButton = Button(context).apply {
            text = "Save Slices"
            setOnClickListener { saveSlices() }
        }
        val controlsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(minusButton)
            addView(sliceCountLabel)
            addView(plusButton)
            addView(saveButton)
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

    companion object {
        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
            Color.rgb(3, 169, 244), Color.rgb(139, 195, 74),
        )
    }
}
