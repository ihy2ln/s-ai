package com.sai.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.sai.core.audio.SampleEditor
import com.sai.core.audio.Wav
import com.sai.core.audio.WavIO
import com.sai.core.tracker.NoteNames
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step
import java.io.File

class PhraseActivity : ComponentActivity() {

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private var phraseId: Int = 0

    private var sampleWav: Wav? = null
    private var sampleSourceName: String = ""
    private var sliceCount = 8

    private lateinit var waveform: WaveformView
    private lateinit var sampleNameLabel: TextView
    private lateinit var sliceCountLabel: TextView
    private lateinit var padContainer: LinearLayout
    private lateinit var stepRows: LinearLayout

    private val importSampleLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Grant couldn't be persisted; the sample still works this session.
            }
            val name = queryDisplayName(uri)
            library.add(listOf(SampleEntry(uri, name)))
            loadSample(uri, name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = TrackerProject(this)
        library = SampleLibrary(this)

        phraseId = intent.getIntExtra(EXTRA_PHRASE_ID, -1)
        if (phraseId < 0) {
            finish()
            return
        }
        if (project.phrases[phraseId] == null) {
            project.putPhrase(phraseId, Phrase.empty())
        }

        setContentView(buildUi())
        refreshSteps()
        refreshSampler()

        val preloadUri = intent.getParcelableExtra<Uri>(SampleEditorActivity.EXTRA_SAMPLE_URI)
        if (preloadUri != null) {
            loadSample(preloadUri, queryDisplayName(preloadUri))
        }
    }

    // --- Layout ---------------------------------------------------------

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "PHRASE %02X".format(phraseId)
            setTextColor(Color.CYAN)
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(title)
            addView(buildSamplerSection())
            addView(View(this@PhraseActivity).apply { setBackgroundColor(Color.rgb(50, 50, 55)) }, dividerParams(density))
            addView(buildStepSection(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun dividerParams(density: Float) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt())

    private fun buildSamplerSection(): LinearLayout {
        val density = resources.displayMetrics.density

        waveform = WaveformView(this)
        sampleNameLabel = TextView(this).apply {
            text = "No sample loaded"
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
        }

        val loadButton = Button(this).apply {
            text = "Load Sample"
            setOnClickListener { showLoadSampleDialog() }
        }
        val minusButton = Button(this).apply {
            text = "-"
            setOnClickListener { changeSliceCount(-1) }
        }
        sliceCountLabel = TextView(this).apply { setTextColor(Color.WHITE) }
        val plusButton = Button(this).apply {
            text = "+"
            setOnClickListener { changeSliceCount(1) }
        }
        val saveButton = Button(this).apply {
            text = "Save Slices"
            setOnClickListener { saveSlices() }
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(loadButton)
            addView(minusButton)
            addView(sliceCountLabel)
            addView(plusButton)
            addView(saveButton)
        }

        padContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(sampleNameLabel)
            addView(waveform, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (110 * density).toInt()))
            addView(controlsRow)
            addView(padContainer)
        }
    }

    private fun buildStepSection(): LinearLayout {
        val header = gridRow(listOf("  ", "NOTE", "INS", "VOL"), Color.rgb(120, 140, 160))
        stepRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(
                ScrollView(this@PhraseActivity).apply { addView(stepRows) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    // --- Sampler (top) ---------------------------------------------------

    private fun showLoadSampleDialog() {
        val entries = library.all()
        val labels = (entries.map { it.displayName } + "Import New...").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Load Sample")
            .setItems(labels) { _, which ->
                if (which == entries.size) {
                    importSampleLauncher.launch(arrayOf("audio/*"))
                } else {
                    loadSample(entries[which].uri, entries[which].displayName)
                }
            }
            .show()
    }

    private fun loadSample(uri: Uri, name: String) {
        val bytes = try {
            contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that file: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val wav = try {
            WavIO.read(bytes)
        } catch (wavError: Exception) {
            try {
                AudioDecoder.decode(contentResolver, uri)
            } catch (decodeError: Exception) {
                Toast.makeText(this, "Unsupported audio file: ${decodeError.message}", Toast.LENGTH_LONG).show()
                return
            }
        }
        sampleWav = wav
        sampleSourceName = name.substringBeforeLast('.')
        sampleNameLabel.text = name
        refreshSampler()
    }

    private fun changeSliceCount(delta: Int) {
        sliceCount = (sliceCount + delta).coerceIn(1, 16)
        refreshSampler()
    }

    private fun sliceBounds(wav: Wav): List<IntRange> {
        val frameCount = wav.frameCount
        return (0 until sliceCount).map { i ->
            val start = i * frameCount / sliceCount
            val end = if (i == sliceCount - 1) frameCount else (i + 1) * frameCount / sliceCount
            start until end
        }
    }

    private fun refreshSampler() {
        sliceCountLabel.text = " %d ".format(sliceCount)
        padContainer.removeAllViews()

        val wav = sampleWav
        if (wav == null) {
            waveform.channels = 1
            waveform.samples = ShortArray(0)
            return
        }

        val bounds = sliceBounds(wav)
        waveform.channels = wav.channels
        waveform.samples = wav.samples
        waveform.sliceBoundaries = bounds.drop(1).map { it.first }

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
                setOnClickListener { previewSlice(wav, range) }
            }
            row!!.addView(pad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun previewSlice(wav: Wav, range: IntRange) {
        val slice = SampleEditor.trim(wav, range.first, range.last + 1)
        AudioPlayback.playOneShot(slice)
    }

    private fun saveSlices() {
        val wav = sampleWav ?: return
        val bounds = sliceBounds(wav)
        val dir = File(filesDir, "slices").apply { mkdirs() }
        val saved = mutableListOf<SampleEntry>()
        for ((index, range) in bounds.withIndex()) {
            val slice = SampleEditor.trim(wav, range.first, range.last + 1)
            val file = File(dir, "$sampleSourceName-slice-${"%02d".format(index)}-${System.currentTimeMillis()}.wav")
            WavIO.write(slice, file)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            saved.add(SampleEntry(uri, "$sampleSourceName #${"%02X".format(index)}"))
        }
        library.add(saved)
        Toast.makeText(this, "Saved ${saved.size} slices to your sample library", Toast.LENGTH_LONG).show()
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "sample"
    }

    // --- Tracker step grid (bottom) --------------------------------------

    private fun refreshSteps() {
        stepRows.removeAllViews()
        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        for (stepIndex in phrase.steps.indices) {
            stepRows.addView(stepRow(stepIndex, phrase.steps[stepIndex]))
        }
    }

    private fun stepRow(stepIndex: Int, step: Step): LinearLayout {
        val density = resources.displayMetrics.density
        val label = "%02X".format(stepIndex)
        val noteText = step.note?.let(NoteNames::format) ?: "---"
        val instrText = step.instrument?.let { "%02X".format(it) } ?: "--"
        val volText = step.volume?.let { "%03d".format(it) } ?: "---"

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cellLabel(label))
            addView(cellValue(noteText) { editNote(stepIndex) }, cellParams(density, 60))
            addView(cellValue(instrText) { editInstrument(stepIndex) }, cellParams(density, 44))
            addView(cellValue(volText) { editVolume(stepIndex) }, cellParams(density, 48))
        }
    }

    private fun cellLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.rgb(90, 110, 130))
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(8, 8, 8, 8)
    }

    private fun cellValue(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(8, 8, 8, 8)
        setOnClickListener { onClick() }
    }

    private fun cellParams(density: Float, widthDp: Int) =
        LinearLayout.LayoutParams((widthDp * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun gridRow(labels: List<String>, color: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val widths = listOf(28, 60, 44, 48)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for ((index, text) in labels.withIndex()) {
                addView(
                    TextView(this@PhraseActivity).apply {
                        this.text = text
                        setTextColor(color)
                        typeface = Typeface.MONOSPACE
                        gravity = Gravity.CENTER
                        setPadding(8, 4, 8, 4)
                    },
                    LinearLayout.LayoutParams((widths[index] * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT),
                )
            }
        }
    }

    private fun editNote(stepIndex: Int) {
        val phrase = project.phrases[phraseId] ?: return
        val current = phrase.steps[stepIndex].note
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current?.toString().orEmpty())
            hint = "0-127, blank to clear"
        }
        AlertDialog.Builder(this)
            .setTitle("Note (step %02X)".format(stepIndex))
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(0, 127)
                updateStep(stepIndex) { it.copy(note = value) }
            }
            .setNeutralButton("Clear") { _, _ -> updateStep(stepIndex) { it.copy(note = null) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editVolume(stepIndex: Int) {
        val phrase = project.phrases[phraseId] ?: return
        val current = phrase.steps[stepIndex].volume
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current?.toString().orEmpty())
            hint = "0-127, blank to clear"
        }
        AlertDialog.Builder(this)
            .setTitle("Volume (step %02X)".format(stepIndex))
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(0, 127)
                updateStep(stepIndex) { it.copy(volume = value) }
            }
            .setNeutralButton("Clear") { _, _ -> updateStep(stepIndex) { it.copy(volume = null) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editInstrument(stepIndex: Int) {
        val entries = library.all()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No samples imported yet")
                .setMessage("Load a sample above, or import from the home screen first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = (entries.indices.map { "%02X  ".format(it) + entries[it].displayName } + "Clear").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Instrument (step %02X)".format(stepIndex))
            .setItems(labels) { _, which ->
                if (which == entries.size) {
                    updateStep(stepIndex) { it.copy(instrument = null) }
                } else {
                    updateStep(stepIndex) { it.copy(instrument = which) }
                }
            }
            .show()
    }

    private fun updateStep(stepIndex: Int, transform: (Step) -> Step) {
        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        steps[stepIndex] = transform(steps[stepIndex])
        project.putPhrase(phraseId, Phrase(steps))
        refreshSteps()
    }

    companion object {
        const val EXTRA_PHRASE_ID = "phrase_id"
        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
            Color.rgb(3, 169, 244), Color.rgb(139, 195, 74),
        )
    }
}
