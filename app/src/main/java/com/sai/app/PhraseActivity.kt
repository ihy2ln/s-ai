package com.sai.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
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
import com.sai.core.tracker.NoteNames
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

class PhraseActivity : ComponentActivity() {

    private enum class ViewMode { SPLIT, SAMPLER_FULL, STEPS_FULL }

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private var phraseId: Int = 0

    private lateinit var rootView: LinearLayout
    private lateinit var samplerPanel: SamplerPanelView
    private lateinit var stepRows: LinearLayout

    private lateinit var samplerSectionWrapper: LinearLayout
    private lateinit var stepSectionWrapper: LinearLayout
    private lateinit var dividerView: View
    private var viewMode = ViewMode.SPLIT

    private val importSampleLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Grant couldn't be persisted; the sample still works this session.
            }
            val name = SampleLoader.queryDisplayName(contentResolver, uri)
            library.add(listOf(SampleEntry(uri, name)))
            loadSample(uri, name)
        }
    }

    private val saveProjectLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) saveProjectTo(uri)
    }

    private val exportMixdownLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-wav")) { uri ->
        if (uri != null) MixdownExporter.writeTo(this, uri)
    }

    private val exportStemsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) MixdownExporter.writeStems(this, uri)
    }

    private val loadProjectLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadProjectFrom(uri)
    }

    private val pickBackgroundImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AppBackground.setImage(this, uri)
            recreate()
        }
    }

    private val pickBackgroundVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AppBackground.setVideo(this, uri)
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = TrackerProjectStore.get(this)
        library = SampleLibrary(this)

        phraseId = intent.getIntExtra(EXTRA_PHRASE_ID, -1)
        if (phraseId < 0) {
            finish()
            return
        }
        if (project.phrases[phraseId] == null) {
            project.putPhrase(phraseId, Phrase.empty())
        }

        setContentView(AppBackground.wrap(this, buildUi()))
        refreshSteps()

        val preloadUri = intent.getParcelableExtra<Uri>(SampleEditorActivity.EXTRA_SAMPLE_URI)
        if (preloadUri != null) {
            loadSample(preloadUri, SampleLoader.queryDisplayName(contentResolver, preloadUri))
        }
    }

    // --- Layout ---------------------------------------------------------

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "PHRASE %02X".format(phraseId)
            setTextColor(AppTheme.accentColor(this@PhraseActivity))
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(PillButton.create(this@PhraseActivity, "E") { showExpand() })
            addView(PillButton.create(this@PhraseActivity, "N") { NavMenu.show(this@PhraseActivity) })
            addView(PillButton.create(this@PhraseActivity, "MX") { EffectsMenu.show(this@PhraseActivity, samplerEffectsTarget()) })
            addView(PillButton.create(this@PhraseActivity, "P") { showProjectMenu() })
            addView(PillButton.create(this@PhraseActivity, "M") { showMenu() })
        }

        samplerSectionWrapper = buildSamplerSection()
        stepSectionWrapper = buildStepSection()
        dividerView = View(this).apply { setBackgroundColor(Color.rgb(50, 50, 55)) }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(titleRow)
            addView(samplerSectionWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(dividerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()))
            addView(stepSectionWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            isLongClickable = true
            setOnLongClickListener { NavMenu.show(this@PhraseActivity); true }
        }

        applyViewMode()
        return rootView
    }

    private fun buildSamplerSection(): LinearLayout {
        val loadButton = Button(this).apply {
            text = "Load Sample"
            setOnClickListener { showLoadSampleDialog() }
        }

        samplerPanel = SamplerPanelView(this).apply {
            onSaveSlices = { sourceName, slices ->
                val saved = SliceExporter.saveToLibrary(this@PhraseActivity, sourceName, slices)
                Toast.makeText(this@PhraseActivity, "Saved ${saved.size} slices to your sample library", Toast.LENGTH_LONG).show()
            }
            onSendToRack = { sourceName, slices ->
                val saved = SliceExporter.saveToLibrary(this@PhraseActivity, sourceName, slices)
                val placed = ChannelRackStore.sendToRack(this@PhraseActivity, saved.map { it.id })
                val extra = saved.size - placed
                val message = if (extra > 0) {
                    "Sent $placed slices to Channel Rack ($extra stayed in the library)"
                } else {
                    "Sent $placed slices to Channel Rack"
                }
                Toast.makeText(this@PhraseActivity, message, Toast.LENGTH_LONG).show()
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(loadButton)
            addView(samplerPanel)
        }
    }

    private fun buildStepSection(): LinearLayout {
        val header = gridRow(listOf("  ", "NOTE", "INS", "VOL", "LEN"), Color.rgb(120, 140, 160))
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

    // --- Full-screen toggle -------------------------------------------------

    private fun applyViewMode() {
        val samplerVisible = viewMode != ViewMode.STEPS_FULL
        val stepsVisible = viewMode != ViewMode.SAMPLER_FULL
        val split = viewMode == ViewMode.SPLIT

        samplerSectionWrapper.visibility = if (samplerVisible) View.VISIBLE else View.GONE
        stepSectionWrapper.visibility = if (stepsVisible) View.VISIBLE else View.GONE
        dividerView.visibility = if (split) View.VISIBLE else View.GONE

        if (samplerVisible) {
            samplerSectionWrapper.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        if (stepsVisible) {
            stepSectionWrapper.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
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
        val wav = try {
            SampleLoader.decode(contentResolver, uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load that file: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        samplerPanel.load(wav, name)
    }

    private fun samplerEffectsTarget() = EffectsTarget(
        getWav = { samplerPanel.currentWav() },
        getName = { samplerPanel.currentSourceName() },
        onApplied = { processed -> samplerPanel.load(processed, samplerPanel.currentSourceName()) },
    )

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
        val lenText = step.length?.let { "%02d".format(it) } ?: "fl"

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cellLabel(label))
            addView(cellValue(noteText) { editNote(stepIndex) }, cellParams(density, 60))
            addView(cellValue(instrText) { editInstrument(stepIndex) }, cellParams(density, 44))
            addView(cellValue(volText) { editVolume(stepIndex) }, cellParams(density, 48))
            addView(cellValue(lenText) { editLength(stepIndex) }, cellParams(density, 40))
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
        val widths = listOf(28, 60, 44, 48, 40)
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
            inputType = InputType.TYPE_CLASS_NUMBER
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
            inputType = InputType.TYPE_CLASS_NUMBER
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

    private fun editLength(stepIndex: Int) {
        val phrase = project.phrases[phraseId] ?: return
        val current = phrase.steps[stepIndex].length
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current?.toString().orEmpty())
            hint = "1–32 16ths, blank = full sample"
        }
        AlertDialog.Builder(this)
            .setTitle("Length (step %02X)".format(stepIndex))
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(1, Phrase.MAX_STEPS)
                updateStep(stepIndex) { it.copy(length = value) }
            }
            .setNeutralButton("Full") { _, _ -> updateStep(stepIndex) { it.copy(length = null) } }
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
        val labels = (entries.map { "%02X  ".format(it.id) + it.displayName } + "Clear").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Instrument (step %02X)".format(stepIndex))
            .setItems(labels) { _, which ->
                if (which == entries.size) {
                    updateStep(stepIndex) { it.copy(instrument = null) }
                } else {
                    updateStep(stepIndex) { it.copy(instrument = entries[which].id) }
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

    // --- Expand (E) -------------------------------------------------------

    private fun showExpand() {
        AlertDialog.Builder(this)
            .setTitle("Expand")
            .setItems(arrayOf("Sampler Full Screen", "Steps Full Screen", "Split View")) { _, which ->
                when (which) {
                    0 -> { viewMode = ViewMode.SAMPLER_FULL; applyViewMode() }
                    1 -> { viewMode = ViewMode.STEPS_FULL; applyViewMode() }
                    2 -> { viewMode = ViewMode.SPLIT; applyViewMode() }
                }
            }
            .show()
    }

    // --- Project (P) ----------------------------------------------------------

    private fun showProjectMenu() {
        ProjectMenu.show(
            this,
            ProjectMenu.Actions(
                onRename = { editProjectName() },
                onSave = { saveProjectLauncher.launch("sai-project.sai.zip") },
                onLoad = { loadProjectLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) },
                onNew = { confirmNewProject() },
                onUndo = { project.undo(); refreshSteps() },
                onRedo = { project.redo(); refreshSteps() },
                onExportWav = { exportMixdownLauncher.launch("sai-mix-${System.currentTimeMillis()}.wav") },
                onExportStems = { exportStemsLauncher.launch("sai-stems-${System.currentTimeMillis()}.zip") },
            ),
        )
    }

    private fun editProjectName() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(project.name)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Project Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ -> project.name = input.text.toString() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmNewProject() {
        AlertDialog.Builder(this)
            .setTitle("New Project")
            .setMessage("Clear the current song and all phrases? Your sample library is kept.")
            .setPositiveButton("New") { _, _ ->
                project.resetProject()
                PlaylistStore.clear(this)
                refreshSteps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Menu (M) -------------------------------------------------------------

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(arrayOf("Manual", "Theme", "Piano Roll")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, ManualActivity::class.java))
                    1 -> showThemeDialog()
                    2 -> startActivity(Intent(this, PianoRollActivity::class.java).putExtra(PianoRollActivity.EXTRA_PHRASE_ID, phraseId))
                }
            }
            .show()
    }

    private fun showThemeDialog() {
        ThemeMenu.show(
            context = this,
            onPickPicture = { pickBackgroundImage.launch(arrayOf("image/*")) },
            onPickVideo = { pickBackgroundVideo.launch(arrayOf("video/*")) },
            onRecreate = { recreate() },
        )
    }

    private fun saveProjectTo(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)!!.use { out ->
                out.write(ProjectBundle.export(this, ModuleLayoutStore.load(this)))
            }
            Toast.makeText(this, "Project package saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadProjectFrom(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            ProjectBundle.import(this, bytes)
            refreshSteps()
            Toast.makeText(this, "Project loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_PHRASE_ID = "phrase_id"
    }
}
