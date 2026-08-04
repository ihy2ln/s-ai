package com.sai.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import java.io.File

class SlicerActivity : ComponentActivity() {

    private lateinit var original: Wav
    private lateinit var sourceName: String
    private lateinit var waveform: WaveformView
    private lateinit var padContainer: LinearLayout
    private lateinit var countLabel: TextView

    private var sliceCount = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.getParcelableExtra<Uri>(SampleEditorActivity.EXTRA_SAMPLE_URI)
        if (uri == null) {
            finish()
            return
        }
        sourceName = queryDisplayName(uri)

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

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).substringBeforeLast('.')
            }
        }
        return "sample"
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val title = TextView(this).apply {
            text = "SLICE"
            setTextColor(Color.CYAN)
            textSize = 20f
        }

        waveform = WaveformView(this)

        countLabel = TextView(this).apply { setTextColor(Color.WHITE) }
        val minusButton = Button(this).apply {
            text = "-"
            setOnClickListener { changeSliceCount(-1) }
        }
        val plusButton = Button(this).apply {
            text = "+"
            setOnClickListener { changeSliceCount(1) }
        }
        val countRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(minusButton)
            addView(countLabel)
            addView(plusButton)
        }

        val saveButton = Button(this).apply {
            text = "Save Slices to Library"
            setOnClickListener { saveSlices() }
        }

        padContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(title)
            addView(waveform, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))
            addView(countRow)
            addView(padContainer)
            addView(saveButton)
        }
    }

    private fun changeSliceCount(delta: Int) {
        sliceCount = (sliceCount + delta).coerceIn(1, 16)
        refresh()
    }

    private fun sliceBounds(): List<IntRange> {
        val frameCount = original.frameCount
        return (0 until sliceCount).map { i ->
            val start = i * frameCount / sliceCount
            val end = if (i == sliceCount - 1) frameCount else (i + 1) * frameCount / sliceCount
            start until end
        }
    }

    private fun refresh() {
        countLabel.text = " %d slices ".format(sliceCount)
        val bounds = sliceBounds()

        waveform.channels = original.channels
        waveform.samples = original.samples
        waveform.sliceBoundaries = bounds.drop(1).map { it.first }

        padContainer.removeAllViews()
        val columns = 4
        var row: LinearLayout? = null
        for ((index, range) in bounds.withIndex()) {
            if (index % columns == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                padContainer.addView(row)
            }
            val pad = Button(this).apply {
                text = "%02X".format(index)
                setBackgroundColor(PALETTE[index % PALETTE.size])
                setTextColor(Color.BLACK)
                setOnClickListener { previewSlice(range) }
            }
            row!!.addView(pad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun previewSlice(range: IntRange) {
        val slice = SampleEditor.trim(original, range.first, range.last + 1)
        AudioPlayback.playOneShot(slice)
    }

    private fun saveSlices() {
        val bounds = sliceBounds()
        val dir = File(filesDir, "slices").apply { mkdirs() }
        val saved = mutableListOf<SampleEntry>()
        for ((index, range) in bounds.withIndex()) {
            val slice = SampleEditor.trim(original, range.first, range.last + 1)
            val file = File(dir, "$sourceName-slice-${"%02d".format(index)}-${System.currentTimeMillis()}.wav")
            WavIO.write(slice, file)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            saved.add(SampleEntry(uri, "$sourceName #${"%02X".format(index)}"))
        }
        SampleLibrary(this).add(saved)
        Toast.makeText(this, "Saved ${saved.size} slices to your sample library", Toast.LENGTH_LONG).show()
    }

    companion object {
        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
            Color.rgb(3, 169, 244), Color.rgb(139, 195, 74),
        )
    }
}
