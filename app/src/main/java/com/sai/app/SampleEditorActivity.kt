package com.sai.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO

class SampleEditorActivity : ComponentActivity() {

    private lateinit var original: Wav
    private lateinit var waveform: WaveformView
    private lateinit var statusText: TextView
    private lateinit var startBar: SeekBar
    private lateinit var endBar: SeekBar
    private lateinit var gainBar: SeekBar
    private lateinit var reverseBox: CheckBox
    private lateinit var normalizeBox: CheckBox

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

        original = try {
            WavIO.read(bytes)
        } catch (wavError: Exception) {
            try {
                AudioDecoder.decode(contentResolver, uri)
            } catch (decodeError: Exception) {
                toastAndFinish("Unsupported audio file: ${decodeError.message}")
                return
            }
        }

        setContentView(buildUi())
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
            addView(label("Gain"))
            addView(gainBar)
            addView(reverseBox)
            addView(normalizeBox)
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

    private fun currentEdit(): Wav {
        val startFrame = (startBar.progress / 1000f * original.frameCount).toInt()
        val endFrame = (endBar.progress / 1000f * original.frameCount).toInt().coerceAtLeast(startFrame)
        var edited = SampleEditor.trim(original, startFrame, endFrame)
        edited = SampleEditor.gain(edited, gainBar.progress - 24.0)
        if (reverseBox.isChecked) edited = SampleEditor.reverse(edited)
        if (normalizeBox.isChecked) edited = SampleEditor.normalize(edited)
        return edited
    }

    private fun refresh() {
        val edited = currentEdit()
        waveform.channels = edited.channels
        waveform.samples = edited.samples
        statusText.text = "${edited.frameCount} frames, ${edited.channels}ch, ${edited.sampleRate}Hz"
    }

    private fun playCurrentEdit() {
        AudioPlayback.playOneShot(currentEdit())
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
