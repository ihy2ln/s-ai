package com.sai.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.SampleWarp
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO

/** Full sample-editing surface: trim/gain/reverse/normalize plus a small "warp" toolkit -
 *  cut/paste, tempo change, pitch shift, granulate, and BPM sync - for reshaping a sample
 *  before it goes into the Sampler/Tracker/Synth. */
class SampleEditorActivity : ComponentActivity() {

    /** The working "tape": Cut/Paste splice this in place. Everything else (trim/gain/reverse/
     *  normalize/tempo/pitch/granulate) is a non-destructive live preview computed on top of it. */
    private lateinit var working: Wav

    private lateinit var waveform: WaveformView
    private lateinit var statusText: TextView
    private lateinit var startBar: SeekBar
    private lateinit var endBar: SeekBar
    private lateinit var gainBar: SeekBar
    private lateinit var reverseBox: CheckBox
    private lateinit var normalizeBox: CheckBox
    private lateinit var tempoBar: SeekBar
    private lateinit var tempoLabel: TextView
    private lateinit var pitchBar: SeekBar
    private lateinit var pitchLabel: TextView
    private lateinit var granulateBox: CheckBox
    private lateinit var grainBar: SeekBar
    private lateinit var scatterBar: SeekBar
    private lateinit var bpmSourceInput: EditText

    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-wav")) { uri ->
        if (uri != null) saveEditedWav(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.getParcelableExtra<Uri>(EXTRA_SAMPLE_URI)
        if (uri == null) {
            finish()
            return
        }

        val bytes = try {
            contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        } catch (e: Exception) {
            toastAndFinish("Couldn't open that file: ${e.message}")
            return
        }

        working = try {
            WavIO.read(bytes)
        } catch (wavError: Exception) {
            try {
                AudioDecoder.decode(contentResolver, uri)
            } catch (decodeError: Exception) {
                toastAndFinish("Unsupported audio file: ${decodeError.message}")
                return
            }
        }

        val root = buildUi()
        setContentView(AppBackground.wrap(this, root))
        refresh()
    }

    private fun toastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        waveform = WaveformView(this)
        statusText = TextView(this).apply { setTextColor(Color.WHITE) }
        startBar = SeekBar(this).apply { max = 1000; progress = 0 }
        endBar = SeekBar(this).apply { max = 1000; progress = 1000 }
        gainBar = SeekBar(this).apply { max = 48; progress = 24 }
        reverseBox = CheckBox(this).apply { text = "Reverse"; setTextColor(Color.WHITE) }
        normalizeBox = CheckBox(this).apply { text = "Normalize"; setTextColor(Color.WHITE) }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = refresh()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        startBar.setOnSeekBarChangeListener(listener)
        endBar.setOnSeekBarChangeListener(listener)
        gainBar.setOnSeekBarChangeListener(listener)
        reverseBox.setOnCheckedChangeListener { _, _ -> refresh() }
        normalizeBox.setOnCheckedChangeListener { _, _ -> refresh() }

        val cutButton = Button(this).apply { text = "Cut"; setOnClickListener { cutSelection() } }
        val pasteButton = Button(this).apply { text = "Paste"; setOnClickListener { pasteAtSelection() } }
        val editRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cutButton)
            addView(pasteButton)
        }

        tempoLabel = TextView(this).apply { setTextColor(Color.WHITE) }
        tempoBar = SeekBar(this).apply { max = 150; progress = 50 } // 50..200%, 50 = 100%
        tempoBar.setOnSeekBarChangeListener(listener)

        pitchLabel = TextView(this).apply { setTextColor(Color.WHITE) }
        pitchBar = SeekBar(this).apply { max = 48; progress = 24 } // -24..+24 semitones
        pitchBar.setOnSeekBarChangeListener(listener)

        granulateBox = CheckBox(this).apply { text = "Granulate"; setTextColor(Color.WHITE) }
        granulateBox.setOnCheckedChangeListener { _, _ -> refresh() }
        grainBar = SeekBar(this).apply { max = 100; progress = 25 } // -> 10..200ms
        grainBar.setOnSeekBarChangeListener(listener)
        scatterBar = SeekBar(this).apply { max = 100; progress = 50 } // 0..100%
        scatterBar.setOnSeekBarChangeListener(listener)

        bpmSourceInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Source BPM"
        }
        val bpmSyncButton = Button(this).apply {
            text = "Sync to Project BPM"
            setOnClickListener { applyBpmSync() }
        }
        val bpmRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(bpmSourceInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(bpmSyncButton)
        }

        val playButton = Button(this).apply {
            text = "Play"
            setOnClickListener { playCurrentEdit() }
        }
        val saveButton = Button(this).apply {
            text = "Save"
            setOnClickListener { saveLauncher.launch(suggestedFileName()) }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(waveform, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (200 * density).toInt()))
            addView(statusText)
            addView(label("Start"))
            addView(startBar)
            addView(label("End"))
            addView(endBar)
            addView(editRow)
            addView(label("Gain"))
            addView(gainBar)
            addView(reverseBox)
            addView(normalizeBox)
            addView(tempoLabel)
            addView(tempoBar)
            addView(pitchLabel)
            addView(pitchBar)
            addView(granulateBox)
            addView(label("Grain size / Scatter"))
            addView(grainBar)
            addView(scatterBar)
            addView(bpmRow)
            addView(
                LinearLayout(this@SampleEditorActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(playButton)
                    addView(saveButton)
                }
            )
        }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
    }

    private fun selectionFrames(): Pair<Int, Int> {
        val startFrame = (startBar.progress / 1000f * working.frameCount).toInt()
        val endFrame = (endBar.progress / 1000f * working.frameCount).toInt().coerceAtLeast(startFrame)
        return startFrame to endFrame
    }

    private fun currentEdit(): Wav {
        val (startFrame, endFrame) = selectionFrames()
        var edited = SampleEditor.trim(working, startFrame, endFrame)
        edited = SampleEditor.gain(edited, gainBar.progress - 24.0)
        if (reverseBox.isChecked) edited = SampleEditor.reverse(edited)
        if (normalizeBox.isChecked) edited = SampleEditor.normalize(edited)

        val tempoPercent = tempoBar.progress + 50
        if (tempoPercent != 100) edited = SampleWarp.timeStretch(edited, 100.0 / tempoPercent)

        val pitchSemitones = pitchBar.progress - 24
        if (pitchSemitones != 0) edited = SampleWarp.pitchShift(edited, pitchSemitones.toDouble())

        if (granulateBox.isChecked) {
            val grainMs = 10.0 + grainBar.progress * 1.9
            val scatter = scatterBar.progress / 100.0
            edited = SampleWarp.granulate(edited, grainMs, scatter)
        }
        return edited
    }

    private fun refresh() {
        tempoLabel.text = "Tempo %d%%".format(tempoBar.progress + 50)
        pitchLabel.text = "Pitch %+dst".format(pitchBar.progress - 24)

        val edited = currentEdit()
        waveform.channels = edited.channels
        waveform.samples = edited.samples
        statusText.text = "${edited.frameCount} frames, ${edited.channels}ch, ${edited.sampleRate}Hz"
    }

    private fun playCurrentEdit() {
        AudioPlayback.playOneShot(currentEdit(), context = this)
    }

    private fun cutSelection() {
        val (startFrame, endFrame) = selectionFrames()
        if (endFrame <= startFrame) {
            Toast.makeText(this, "Select a range first (Start/End sliders)", Toast.LENGTH_SHORT).show()
            return
        }
        val clip = SampleEditor.trim(working, startFrame, endFrame)
        SampleClipboard.wav = clip
        working = SampleEditor.cut(working, startFrame, endFrame)
        startBar.progress = 0
        endBar.progress = 1000
        Toast.makeText(this, "Cut ${clip.frameCount} frames to clipboard", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun pasteAtSelection() {
        val clip = SampleClipboard.wav
        if (clip == null) {
            Toast.makeText(this, "Nothing cut yet", Toast.LENGTH_SHORT).show()
            return
        }
        val (startFrame, _) = selectionFrames()
        working = try {
            SampleEditor.insert(working, startFrame, clip)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, "Can't paste: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        endBar.progress = 1000
        refresh()
    }

    private fun applyBpmSync() {
        val sourceBpm = bpmSourceInput.text.toString().toDoubleOrNull()
        if (sourceBpm == null || sourceBpm <= 0.0) {
            Toast.makeText(this, "Enter this sample's source BPM first", Toast.LENGTH_SHORT).show()
            return
        }
        val targetBpm = TrackerProjectStore.get(this).bpm.toDouble()
        val newTempoPercent = (100.0 * targetBpm / sourceBpm).coerceIn(50.0, 200.0)
        tempoBar.progress = (newTempoPercent - 50).toInt().coerceIn(0, tempoBar.max)
        Toast.makeText(this, "Tempo set to match project BPM (%d)".format(targetBpm.toInt()), Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun suggestedFileName(): String = "sai-edited-${System.currentTimeMillis()}.wav"

    private fun saveEditedWav(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)!!.use { out -> WavIO.write(currentEdit(), out) }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_SAMPLE_URI = "sample_uri"
    }
}
