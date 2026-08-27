package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.sai.core.project.StableIds
import com.sai.core.stem.StemBackend
import com.sai.core.stem.StemKind
import com.sai.core.stem.StemSplitJob
import com.sai.core.stem.StemSplitMode
import com.sai.core.stem.StemSplitOutput
import com.sai.core.stem.StemSplitPhase
import com.sai.core.stem.StemSplitSettings
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Stem splitter UI: choose stems, run a ComfyUI Demucs workflow on your PC, save results to the library.
 */
class StemSplitterActivity : ComponentActivity() {

    private lateinit var settingsStore: StemSplitterSettingsStore
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sampleLabel: TextView
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button
    private lateinit var toRackBox: CheckBox

    private var sampleUri: Uri? = null
    private var sampleId: Int = StableIds.UNASSIGNED
    private var sampleName: String = ""
    private var activeJob: StemSplitJob? = null
    private var worker: Thread? = null

    private val stemChecks = linkedMapOf<StemKind, CheckBox>()
    private lateinit var modeGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = StemSplitterSettingsStore(this)
        sampleUri = intent.getParcelableExtra(EXTRA_SAMPLE_URI)
        sampleId = intent.getIntExtra(EXTRA_SAMPLE_ID, StableIds.UNASSIGNED)
        sampleName = intent.getStringExtra(EXTRA_SAMPLE_NAME).orEmpty()
        setContentView(AppBackground.wrap(this, buildUi()))
        refreshSampleLabel()
    }

    override fun onDestroy() {
        activeJob?.cancel()
        worker?.interrupt()
        super.onDestroy()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val accent = AppTheme.accentColor(this)

        val title = TextView(this).apply {
            text = "Split Stems"
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 18f
        }
        val guideButton = PillButton.create(this, "?") {
            startActivity(Intent(this, ManualActivity::class.java))
        }
        val settingsButton = PillButton.create(this, "⚙") { showSettingsDialog() }
        val navButton = PillButton.create(this, "N") { NavMenu.show(this) }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(guideButton)
            addView(settingsButton)
            addView(navButton)
        }

        sampleLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        val pickButton = Button(this).apply {
            text = "Choose Sample"
            setOnClickListener { pickSampleFromLibrary() }
        }

        modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(radioForMode(StemSplitMode.FOUR_STEM, settingsStore.defaultMode() == StemSplitMode.FOUR_STEM))
            addView(radioForMode(StemSplitMode.TWO_STEM, settingsStore.defaultMode() == StemSplitMode.TWO_STEM))
            setOnCheckedChangeListener { _, checkedId ->
                val mode = if (checkedId == MODE_TWO_ID) StemSplitMode.TWO_STEM else StemSplitMode.FOUR_STEM
                settingsStore.setDefaultMode(mode)
                applyMode(mode)
            }
        }

        val stemsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        for (kind in StemKind.entries) {
            val box = CheckBox(this).apply {
                text = kind.label
                setTextColor(Color.WHITE)
                isChecked = settingsStore.defaultMode().defaultKinds().contains(kind)
            }
            stemChecks[kind] = box
            stemsColumn.addView(box)
        }
        applyMode(settingsStore.defaultMode())

        toRackBox = CheckBox(this).apply {
            text = "Also send stems to Channel Rack"
            setTextColor(Color.WHITE)
            isChecked = settingsStore.sendToRackAfterSplit()
            setOnCheckedChangeListener { _, checked -> settingsStore.setSendToRackAfterSplit(checked) }
        }

        statusText = TextView(this).apply {
            setTextColor(Color.rgb(160, 170, 180))
            text = "Runs Demucs on your ComfyUI PC. Set server URL in ⚙."
            textSize = 13f
        }
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }

        startButton = Button(this).apply {
            text = "Split"
            setOnClickListener { startSplit() }
        }
        cancelButton = Button(this).apply {
            text = "Cancel"
            visibility = View.GONE
            setOnClickListener {
                activeJob?.cancel()
                setRunning(false, "Cancelling…")
            }
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(startButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow)
            addView(divider(density))
            addView(sampleLabel)
            addView(pickButton)
            addView(sectionLabel("Mode", accent))
            addView(modeGroup)
            addView(sectionLabel("Stems to keep", accent))
            addView(stemsColumn)
            addView(toRackBox)
            addView(divider(density))
            addView(statusText)
            addView(progressBar)
            addView(actions)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(
                ScrollView(this@StemSplitterActivity).apply { addView(body) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT),
            )
        }
    }

    private fun radioForMode(mode: StemSplitMode, checked: Boolean): RadioButton =
        RadioButton(this).apply {
            id = if (mode == StemSplitMode.TWO_STEM) MODE_TWO_ID else MODE_FOUR_ID
            text = mode.label
            setTextColor(Color.WHITE)
            isChecked = checked
        }

    private fun sectionLabel(text: String, accent: Int): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(accent)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }

    private fun divider(density: Float): View =
        View(this).apply {
            setBackgroundColor(Color.rgb(50, 50, 55))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (8 * density).toInt()
            }
        }

    private fun applyMode(mode: StemSplitMode) {
        val allowed = mode.defaultKinds()
        for ((kind, box) in stemChecks) {
            box.isEnabled = kind in allowed
            if (kind in allowed && !box.isChecked) box.isChecked = true
            if (kind !in allowed) box.isChecked = false
        }
    }

    private fun currentMode(): StemSplitMode =
        if (modeGroup.checkedRadioButtonId == MODE_TWO_ID) StemSplitMode.TWO_STEM else StemSplitMode.FOUR_STEM

    private fun selectedStems(): Set<StemKind> =
        stemChecks.filterValues { it.isChecked && it.isEnabled }.keys

    private fun refreshSampleLabel() {
        sampleLabel.text = if (sampleUri != null) {
            "Sample: ${sampleName.ifBlank { "loaded" }}"
        } else {
            "No sample selected — choose from your library"
        }
    }

    private fun pickSampleFromLibrary() {
        val entries = SampleLibrary(this).all()
        if (entries.isEmpty()) {
            Toast.makeText(this, "Import a sample first (Menu → Samples).", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Sample")
            .setItems(entries.map { it.displayName }.toTypedArray()) { _, which ->
                val entry = entries[which]
                sampleUri = entry.uri
                sampleId = entry.id
                sampleName = entry.displayName
                refreshSampleLabel()
            }
            .show()
    }

    private fun showSettingsDialog() {
        val current = settingsStore.load()
        val urlInput = EditText(this).apply {
            setText(current.comfyBaseUrl)
            hint = "http://192.168.1.10:8188"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val apiKeyInput = EditText(this).apply {
            setText(current.comfyApiKey)
            hint = "Optional API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val modelInput = EditText(this).apply {
            setText(current.demucsModel)
            hint = "htdemucs"
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(label("ComfyUI server URL"))
            addView(urlInput)
            addView(label("API key (optional)"))
            addView(apiKeyInput)
            addView(label("Demucs model"))
            addView(modelInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Stem Splitter settings")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                settingsStore.save(
                    current.copy(
                        backend = StemBackend.COMFY_UI,
                        comfyBaseUrl = urlInput.text.toString().trim(),
                        comfyApiKey = apiKeyInput.text.toString().trim(),
                        demucsModel = modelInput.text.toString().trim().ifBlank { "htdemucs" },
                    ),
                )
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Test") { _, _ ->
                testConnection(
                    current.copy(
                        comfyBaseUrl = urlInput.text.toString().trim(),
                        comfyApiKey = apiKeyInput.text.toString().trim(),
                    ),
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(140, 150, 160))
            textSize = 12f
        }

    private fun testConnection(settings: StemSplitSettings) {
        if (settings.comfyBaseUrl.isBlank()) {
            Toast.makeText(this, "Enter a ComfyUI URL first", Toast.LENGTH_SHORT).show()
            return
        }
        thread(name = "comfy-ping") {
            val result = com.sai.core.stem.ComfyUiClient(settings).ping()
            runOnUiThread {
                if (result.isSuccess) {
                    Toast.makeText(this, "ComfyUI reachable", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Can't reach ComfyUI: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun startSplit() {
        val uri = sampleUri
        if (uri == null) {
            Toast.makeText(this, "Choose a sample first", Toast.LENGTH_SHORT).show()
            return
        }
        val settings = settingsStore.load()
        if (settings.comfyBaseUrl.isBlank()) {
            Toast.makeText(this, "Set ComfyUI server URL in ⚙ settings", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            return
        }
        val stems = selectedStems()
        if (stems.isEmpty()) {
            Toast.makeText(this, "Select at least one stem", Toast.LENGTH_SHORT).show()
            return
        }

        val wav = try {
            SampleLoader.decode(contentResolver, uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load sample: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val workflow = try {
            assets.open(WORKFLOW_ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Toast.makeText(this, "Missing workflow asset: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val job = StemSplitJob(settings)
        activeJob = job
        setRunning(true, "Uploading to ComfyUI…")
        val phaseRef = AtomicReference<StemSplitPhase>(StemSplitPhase.Idle)
        worker = thread(name = "stem-split") {
            val phase = job.run(
                input = wav,
                sourceName = sampleName.substringBeforeLast('.').ifBlank { "sample" },
                mode = currentMode(),
                requestedStems = stems,
                workflowJson = workflow,
                onPhase = { update ->
                    phaseRef.set(update)
                    runOnUiThread { renderPhase(update) }
                },
            )
            phaseRef.set(phase)
            runOnUiThread {
                renderPhase(phase)
                activeJob = null
                worker = null
            }
        }
    }

    private fun renderPhase(phase: StemSplitPhase) {
        when (phase) {
            is StemSplitPhase.Uploading -> setRunning(true, "Uploading audio…")
            is StemSplitPhase.Processing -> setRunning(true, phase.message)
            is StemSplitPhase.Downloading -> setRunning(true, "Downloading ${phase.stem.label} (${phase.index}/${phase.total})…")
            is StemSplitPhase.Complete -> {
                setRunning(false, "Done — saved ${phase.outputs.size} stems to your library")
                saveOutputs(phase.outputs)
            }
            is StemSplitPhase.Failed -> setRunning(false, phase.message)
            StemSplitPhase.Cancelled -> setRunning(false, "Cancelled")
            StemSplitPhase.Idle -> Unit
        }
    }

    private fun saveOutputs(outputs: List<StemSplitOutput>) {
        val baseName = sampleName.substringBeforeLast('.').ifBlank { "sample" }
        val saved = mutableListOf<SampleEntry>()
        outputs.forEach { output ->
            val name = "$baseName — ${output.kind.label}"
            saved += SliceExporter.saveNamed(this, name, output.wav, kindCategory(output.kind))
        }
        if (toRackBox.isChecked && saved.isNotEmpty()) {
            val placed = ChannelRackStore.sendToRack(this, saved.map { it.id })
            val extra = saved.size - placed
            val rackMsg = if (extra > 0) {
                " Placed $placed on Channel Rack ($extra stayed in library)."
            } else {
                " Placed $placed on Channel Rack."
            }
            statusText.text = statusText.text.toString() + rackMsg
        }
        Toast.makeText(this, "Saved ${saved.size} stems to library", Toast.LENGTH_LONG).show()
    }

    private fun kindCategory(kind: StemKind): String = when (kind) {
        StemKind.VOCALS -> SoundCategory.VOCALS
        StemKind.DRUMS -> SoundCategory.PERCUSSION
        StemKind.BASS -> SoundCategory.SAMPLES
        StemKind.OTHER -> SoundCategory.SAMPLES
        StemKind.INSTRUMENTAL -> SoundCategory.SAMPLES
    }

    private fun setRunning(running: Boolean, message: String) {
        statusText.text = message
        progressBar.visibility = if (running) View.VISIBLE else View.GONE
        startButton.isEnabled = !running
        cancelButton.visibility = if (running) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_SAMPLE_URI = "sample_uri"
        const val EXTRA_SAMPLE_ID = "sample_id"
        const val EXTRA_SAMPLE_NAME = "sample_name"
        private const val WORKFLOW_ASSET = "comfyui/demucs-4stem.json"
        private const val MODE_TWO_ID = 10_001
        private const val MODE_FOUR_ID = 10_002

        fun open(context: Context, entry: SampleEntry? = null) {
            context.startActivity(
                Intent(context, StemSplitterActivity::class.java).apply {
                    if (entry != null) {
                        putExtra(EXTRA_SAMPLE_URI, entry.uri)
                        putExtra(EXTRA_SAMPLE_ID, entry.id)
                        putExtra(EXTRA_SAMPLE_NAME, entry.displayName)
                    }
                },
            )
        }
    }
}
