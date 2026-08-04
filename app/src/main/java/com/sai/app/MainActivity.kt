package com.sai.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
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
import com.sai.core.tracker.Phrase

class MainActivity : ComponentActivity() {

    private lateinit var library: SampleLibrary
    private lateinit var project: TrackerProject
    private lateinit var sequencer: Sequencer

    private lateinit var samplerPanel: SamplerPanelView
    private lateinit var sampleListContainer: LinearLayout

    private lateinit var songRows: LinearLayout
    private lateinit var bpmLabel: TextView
    private lateinit var playButton: Button
    private lateinit var statusText: TextView

    private var highlightedPosition = -1
    private val songRowViews = mutableListOf<LinearLayout>()

    private val openSamples = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importSamples(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = SampleLibrary(this)
        project = TrackerProject(this)
        sequencer = Sequencer(contentResolver, library.all())
        sequencer.onPositionChanged = { position, step -> runOnUiThread { highlightPosition(position, step) } }

        setContentView(buildUi())
        refreshSampleList()
        refreshSongGrid()
    }

    override fun onResume() {
        super.onResume()
        refreshSampleList()
    }

    override fun onPause() {
        super.onPause()
        sequencer.stop()
        playButton.text = "Play"
    }

    // --- Layout -----------------------------------------------------------

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "S.Ai"
            setTextColor(Color.WHITE)
            textSize = 24f
        }
        val menuButton = Button(this).apply {
            text = "Menu"
            setOnClickListener { showMenu() }
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(menuButton)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(headerRow)
            addView(buildSamplerSection(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                View(this@MainActivity).apply { setBackgroundColor(Color.rgb(50, 50, 55)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()),
            )
            addView(buildTrackerSection(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun buildSamplerSection(): LinearLayout {
        val density = resources.displayMetrics.density

        val title = TextView(this).apply {
            text = "SAMPLER"
            setTextColor(Color.CYAN)
            textSize = 16f
        }

        samplerPanel = SamplerPanelView(this).apply {
            onSaveSlices = { sourceName, slices ->
                val saved = SliceExporter.saveToLibrary(this@MainActivity, sourceName, slices)
                Toast.makeText(this@MainActivity, "Saved ${saved.size} slices to your sample library", Toast.LENGTH_LONG).show()
                refreshSampleList()
            }
        }

        sampleListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(samplerPanel)
            addView(
                ScrollView(this@MainActivity).apply { addView(sampleListContainer) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (100 * density).toInt()),
            )
        }
    }

    private fun buildTrackerSection(): LinearLayout {
        val title = TextView(this).apply {
            text = "TRACKER"
            setTextColor(Color.CYAN)
            textSize = 16f
        }

        bpmLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setOnClickListener { editBpm() }
        }
        playButton = Button(this).apply {
            text = "Play"
            setOnClickListener { togglePlayback() }
        }
        statusText = TextView(this).apply { setTextColor(Color.rgb(90, 200, 200)) }
        val transport = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(bpmLabel)
            addView(playButton)
            addView(statusText)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(headerCell("  "))
            for (track in 0 until project.song.trackCount) headerCell((track + 1).toString())
        }

        songRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(transport)
            addView(header)
            addView(
                ScrollView(this@MainActivity).apply { addView(songRows) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun headerCell(text: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(120, 140, 160))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    // --- Sampler section ----------------------------------------------------

    private fun refreshSampleList() {
        sampleListContainer.removeAllViews()
        val entries = library.all()
        if (entries.isEmpty()) {
            sampleListContainer.addView(label("No samples yet. Tap Menu > Add Samples."))
            return
        }
        for ((index, entry) in entries.withIndex()) {
            sampleListContainer.addView(sampleRow(entry, PALETTE[index % PALETTE.size]))
        }
    }

    private fun sampleRow(entry: SampleEntry, accent: Int): LinearLayout {
        val density = resources.displayMetrics.density

        val accentStrip = View(this).apply { setBackgroundColor(accent) }
        val nameButton = Button(this).apply {
            text = entry.displayName
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = null
            setOnClickListener { loadIntoSampler(entry) }
            setOnLongClickListener {
                startActivity(Intent(this@MainActivity, SampleEditorActivity::class.java).putExtra(SampleEditorActivity.EXTRA_SAMPLE_URI, entry.uri))
                true
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(30, 30, 34))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, (2 * density).toInt(), 0, (2 * density).toInt())
            }
            addView(accentStrip, LinearLayout.LayoutParams((6 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(nameButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun loadIntoSampler(entry: SampleEntry) {
        val wav = try {
            SampleLoader.decode(contentResolver, entry.uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load ${entry.displayName}: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        samplerPanel.load(wav, entry.displayName)
    }

    private fun importSamples(uris: List<Uri>) {
        val entries = uris.map { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Grant couldn't be persisted (e.g. per-app URI grant limit); the sample
                // still works this session, it just won't survive an app restart.
            }
            SampleEntry(uri, SampleLoader.queryDisplayName(contentResolver, uri))
        }
        library.add(entries)
        refreshSampleList()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
    }

    // --- Tracker section ------------------------------------------------------

    private fun refreshSongGrid() {
        bpmLabel.text = " BPM %d ".format(project.bpm)
        songRows.removeAllViews()
        songRowViews.clear()
        for (position in project.song.positions.indices) {
            val row = songRow(position)
            songRowViews.add(row)
            songRows.addView(row)
        }
    }

    private fun songRow(position: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        row.addView(
            TextView(this).apply {
                text = "%02X".format(position)
                setTextColor(Color.rgb(90, 110, 130))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        )

        val slots = project.song.positions[position]
        for (track in slots.indices) {
            val phraseId = slots[track]
            row.addView(
                TextView(this).apply {
                    text = phraseId?.let { "%02X".format(it) } ?: "--"
                    setTextColor(if (phraseId != null) Color.WHITE else Color.rgb(60, 70, 80))
                    gravity = Gravity.CENTER
                    setPadding(4, 8, 4, 8)
                    layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                    setOnClickListener { onSlotTapped(position, track) }
                }
            )
        }
        return row
    }

    private fun highlightPosition(position: Int, step: Int) {
        if (highlightedPosition in songRowViews.indices) {
            songRowViews[highlightedPosition].setBackgroundColor(Color.TRANSPARENT)
        }
        if (position in songRowViews.indices) {
            songRowViews[position].setBackgroundColor(Color.rgb(0, 50, 55))
        }
        highlightedPosition = position
        statusText.text = " %02X:%X".format(position, step)
    }

    private fun onSlotTapped(position: Int, track: Int) {
        val current = project.song.positions[position][track]
        if (current == null) {
            AlertDialog.Builder(this)
                .setTitle("Empty slot")
                .setItems(arrayOf("New Phrase", "Assign Existing #")) { _, which ->
                    when (which) {
                        0 -> {
                            val id = project.nextPhraseId()
                            project.putPhrase(id, Phrase.empty())
                            project.setSongSlot(position, track, id)
                            refreshSongGrid()
                            openPhrase(id)
                        }
                        1 -> promptAssignExisting(position, track)
                    }
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Phrase %02X".format(current))
                .setItems(arrayOf("Edit", "Clear")) { _, which ->
                    when (which) {
                        0 -> openPhrase(current)
                        1 -> {
                            project.setSongSlot(position, track, null)
                            refreshSongGrid()
                        }
                    }
                }
                .show()
        }
    }

    private fun promptAssignExisting(position: Int, track: Int) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this)
            .setTitle("Phrase number")
            .setView(input)
            .setPositiveButton("Assign") { _, _ ->
                val id = input.text.toString().toIntOrNull()
                if (id != null) {
                    project.setSongSlot(position, track, id)
                    refreshSongGrid()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPhrase(id: Int) {
        startActivity(Intent(this, PhraseActivity::class.java).putExtra(PhraseActivity.EXTRA_PHRASE_ID, id))
    }

    private fun editBpm() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(project.bpm.toString())
        }
        AlertDialog.Builder(this)
            .setTitle("BPM")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(20, 300)
                if (value != null) {
                    project.bpm = value
                    refreshSongGrid()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun togglePlayback() {
        if (sequencer.isRunning) {
            sequencer.stop()
            playButton.text = "Play"
        } else {
            sequencer = Sequencer(contentResolver, library.all())
            sequencer.onPositionChanged = { position, step -> runOnUiThread { highlightPosition(position, step) } }
            sequencer.start(project.song, project.phrases, project.bpm)
            playButton.text = "Stop"
        }
    }

    // --- Menu ---------------------------------------------------------------

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(arrayOf("Add Samples", "Plugins")) { _, which ->
                when (which) {
                    0 -> openSamples.launch(arrayOf("audio/*"))
                    1 -> showPluginsDialog()
                }
            }
            .show()
    }

    private fun showPluginsDialog() {
        val plugins = PluginRegistry.available
        if (plugins.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Plugins")
                .setMessage("No plugins available yet. Future instrument and effect plugins will appear here, with a toggle to enable or disable each one.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val settings = PluginSettings(this)
        val names = plugins.map { it.name }.toTypedArray()
        val checked = BooleanArray(plugins.size) { settings.isEnabled(plugins[it].id) }
        AlertDialog.Builder(this)
            .setTitle("Plugins")
            .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                settings.setEnabled(plugins[index].id, isChecked)
            }
            .setPositiveButton("Done", null)
            .show()
    }

    companion object {
        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
        )
    }
}
