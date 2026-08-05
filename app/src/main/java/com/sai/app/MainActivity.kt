package com.sai.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sai.core.tracker.Phrase
import com.sai.core.tracker.Step

class MainActivity : ComponentActivity() {

    private enum class ViewMode { SPLIT, SAMPLER_FULL, SYNTH_FULL, TRACKER_FULL }

    private lateinit var library: SampleLibrary
    private lateinit var project: TrackerProject
    private lateinit var sequencer: Sequencer

    private lateinit var rootView: LinearLayout

    private lateinit var samplerPanel: SamplerPanelView
    private lateinit var sampleListContainer: LinearLayout
    private lateinit var samplerSectionWrapper: LinearLayout

    private lateinit var synthPanel: SynthPanelView
    private lateinit var synthSectionWrapper: LinearLayout

    private lateinit var songRows: LinearLayout
    private lateinit var bpmLabel: TextView
    private lateinit var playButton: Button
    private lateinit var statusText: TextView
    private lateinit var tempoDot: ImageView
    private lateinit var tempoBouncer: TempoBouncer
    private lateinit var trackerSectionWrapper: LinearLayout

    private lateinit var dividerView: View
    private lateinit var dividerView2: View

    private var viewMode = ViewMode.SPLIT
    private var highlightedPosition = -1
    private var lastLiveStep = 0
    private val songRowViews = mutableListOf<LinearLayout>()

    private val audioRecorder = AudioRecorder()
    private lateinit var recordAudioButton: Button
    private lateinit var recordArmButton: Button
    private lateinit var routeLabel: TextView
    private var recordArmed = false
    private val recordTrack = 0

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            runOnUiThread { updateRouteLabel() }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            runOnUiThread { updateRouteLabel() }
        }
    }

    private val openSamples = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importSamples(uris)
    }

    private val saveProjectLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) saveProjectTo(uri)
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

    private val requestRecordAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startAudioRecording()
        } else {
            Toast.makeText(this, "Microphone permission is needed to record audio", Toast.LENGTH_LONG).show()
        }
    }

    private val openSynthSample = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Grant couldn't be persisted; the sample still works this session.
            }
            val name = SampleLoader.queryDisplayName(contentResolver, uri)
            library.add(listOf(SampleEntry(uri, name)))
            try {
                synthPanel.load(SampleLoader.decode(contentResolver, uri), name)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't load that file: ${e.message}", Toast.LENGTH_LONG).show()
            }
            refreshSampleList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = SampleLibrary(this)
        project = TrackerProjectStore.get(this)
        sequencer = Sequencer(this, library.all())
        sequencer.onPositionChanged = { position, step -> runOnUiThread { highlightPosition(position, step) } }

        setContentView(AppBackground.wrap(this, buildUi()))
        refreshSampleList()
        refreshSongGrid()
        updateRouteLabel()
        (getSystemService(AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    override fun onResume() {
        super.onResume()
        refreshSampleList()
        tempoBouncer.setBpm(project.bpm)
        updateRouteLabel()
    }

    override fun onPause() {
        super.onPause()
        sequencer.stop()
        playButton.text = "Play"
        tempoBouncer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(audioDeviceCallback)
        if (audioRecorder.isRecording) audioRecorder.stop()
    }

    private fun updateRouteLabel() {
        routeLabel.text = when (AudioRoute.current(this)) {
            Route.HEADPHONES -> "🎧"
            Route.BLUETOOTH -> "📶"
            Route.SPEAKER -> "🔊"
            Route.UNKNOWN -> "🔈"
        }
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
        routeLabel = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, (6 * density).toInt(), 0)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(routeLabel)
            addView(PillButton.create(this@MainActivity, "E") { showExpand() })
            addView(PillButton.create(this@MainActivity, "N") { showNav() })
            addView(PillButton.create(this@MainActivity, "MX") { EffectsMenu.show(this@MainActivity, samplerEffectsTarget()) })
            addView(PillButton.create(this@MainActivity, "M") { showMenu() })
        }

        samplerSectionWrapper = buildSamplerSection()
        synthSectionWrapper = buildSynthSection()
        trackerSectionWrapper = buildTrackerSection()
        dividerView = View(this).apply { setBackgroundColor(Color.rgb(50, 50, 55)) }
        dividerView2 = View(this).apply { setBackgroundColor(Color.rgb(50, 50, 55)) }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(headerRow)
            addView(samplerSectionWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(dividerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()))
            addView(synthSectionWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(dividerView2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()))
            addView(trackerSectionWrapper, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            isLongClickable = true
            setOnLongClickListener { showNav(); true }
        }

        applyViewMode()
        return rootView
    }

    private fun buildSamplerSection(): LinearLayout {
        val density = resources.displayMetrics.density

        val title = TextView(this).apply {
            text = "SAMPLER"
            setTextColor(AppTheme.accentColor(this@MainActivity))
            textSize = 16f
        }
        val addButton = Button(this).apply {
            text = "+"
            setOnClickListener { openSamples.launch(arrayOf("audio/*")) }
        }
        recordAudioButton = Button(this).apply {
            text = "Record Audio"
            setOnClickListener { toggleAudioRecording() }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(recordAudioButton)
            addView(addButton)
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
            addView(titleRow)
            addView(samplerPanel)
            addView(
                ScrollView(this@MainActivity).apply { addView(sampleListContainer) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (100 * density).toInt()),
            )
        }
    }

    private fun buildSynthSection(): LinearLayout {
        val title = TextView(this).apply {
            text = "SYNTH"
            setTextColor(AppTheme.accentColor(this@MainActivity))
            textSize = 16f
        }
        val addButton = Button(this).apply {
            text = "+"
            setOnClickListener { openSynthSample.launch(arrayOf("audio/*")) }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(addButton)
        }

        synthPanel = SynthPanelView(this).apply {
            onSaveToLibrary = { sourceName, wav ->
                val saved = SliceExporter.saveToLibrary(this@MainActivity, sourceName, listOf(wav))
                Toast.makeText(this@MainActivity, "Saved ${saved.size} to your sample library", Toast.LENGTH_LONG).show()
                refreshSampleList()
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow)
            addView(synthPanel)
        }
    }

    private fun buildTrackerSection(): LinearLayout {
        val title = TextView(this).apply {
            text = "TRACKER"
            setTextColor(AppTheme.accentColor(this@MainActivity))
            textSize = 16f
        }
        val addButton = Button(this).apply {
            text = "+"
            setOnClickListener { openSamples.launch(arrayOf("audio/*")) }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(addButton)
        }

        val density = resources.displayMetrics.density
        bpmLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setOnClickListener { editBpm() }
        }
        playButton = Button(this).apply {
            text = "Play"
            setOnClickListener { togglePlayback() }
        }
        tempoDot = ImageView(this).apply {
            setImageResource(R.drawable.tempo_dot)
            layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt()).apply {
                setMargins((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        tempoBouncer = TempoBouncer(tempoDot, 14 * density)
        statusText = TextView(this).apply { setTextColor(Color.rgb(90, 200, 200)) }
        recordArmButton = Button(this).apply {
            text = "REC"
            setOnClickListener {
                recordArmed = !recordArmed
                setBackgroundColor(if (recordArmed) Color.rgb(200, 40, 40) else Color.DKGRAY)
                if (recordArmed) Toast.makeText(this@MainActivity, "Live record armed: tap a sample to punch it in while playing", Toast.LENGTH_LONG).show()
            }
        }
        val transport = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bpmLabel)
            addView(playButton)
            addView(recordArmButton)
            addView(tempoDot)
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
            addView(titleRow)
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

    // --- Full-screen toggle -------------------------------------------------

    private fun showSplitView() {
        viewMode = ViewMode.SPLIT
        applyViewMode()
    }

    private fun applyViewMode() {
        val split = viewMode == ViewMode.SPLIT
        val samplerVisible = split || viewMode == ViewMode.SAMPLER_FULL
        val synthVisible = split || viewMode == ViewMode.SYNTH_FULL
        val trackerVisible = split || viewMode == ViewMode.TRACKER_FULL

        samplerSectionWrapper.visibility = if (samplerVisible) View.VISIBLE else View.GONE
        synthSectionWrapper.visibility = if (synthVisible) View.VISIBLE else View.GONE
        trackerSectionWrapper.visibility = if (trackerVisible) View.VISIBLE else View.GONE
        dividerView.visibility = if (split) View.VISIBLE else View.GONE
        dividerView2.visibility = if (split) View.VISIBLE else View.GONE

        if (samplerVisible) {
            samplerSectionWrapper.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        if (synthVisible) {
            synthSectionWrapper.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        if (trackerVisible) {
            trackerSectionWrapper.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
    }

    // --- Sampler section ----------------------------------------------------

    private fun refreshSampleList() {
        sampleListContainer.removeAllViews()
        val entries = library.all()
        if (entries.isEmpty()) {
            sampleListContainer.addView(label("No samples yet. Tap M > Samples or Sounds."))
            return
        }
        for ((index, entry) in entries.withIndex()) {
            sampleListContainer.addView(sampleRow(entry, index, PALETTE[index % PALETTE.size]))
        }
    }

    private fun sampleRow(entry: SampleEntry, instrumentIndex: Int, accent: Int): LinearLayout {
        val density = resources.displayMetrics.density

        val accentStrip = View(this).apply { setBackgroundColor(accent) }
        val nameButton = Button(this).apply {
            text = entry.displayName
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = null
            setOnClickListener {
                if (recordArmed && sequencer.isRunning) {
                    recordLiveHit(entry, instrumentIndex)
                } else {
                    loadIntoSampler(entry)
                }
            }
            setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(entry.displayName)
                    .setItems(arrayOf("Edit", "Mixer")) { _, which ->
                        when (which) {
                            0 -> startActivity(Intent(this@MainActivity, SampleEditorActivity::class.java).putExtra(SampleEditorActivity.EXTRA_SAMPLE_URI, entry.uri))
                            1 -> EffectsMenu.show(this@MainActivity, libraryEffectsTarget(entry))
                        }
                    }
                    .show()
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

    private fun samplerEffectsTarget() = EffectsTarget(
        getWav = { samplerPanel.currentWav() },
        getName = { samplerPanel.currentSourceName() },
        onApplied = { processed -> samplerPanel.load(processed, samplerPanel.currentSourceName()) },
    )

    private fun libraryEffectsTarget(entry: SampleEntry) = EffectsTarget(
        getWav = {
            try {
                SampleLoader.decode(contentResolver, entry.uri)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't load ${entry.displayName}: ${e.message}", Toast.LENGTH_LONG).show()
                null
            }
        },
        getName = { entry.displayName },
        onApplied = { processed ->
            SliceExporter.saveToLibrary(this, entry.displayName, listOf(processed))
            refreshSampleList()
        },
    )

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
        tempoBouncer.setBpm(project.bpm)
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
        lastLiveStep = step
        statusText.text = " %02X:%X".format(position, step)
    }

    /** Records a live "session" hit: plays the sample immediately and writes it into the phrase
     *  at the sequencer's current position/step, similar to punching in a pad hit while playing. */
    private fun recordLiveHit(entry: SampleEntry, instrumentIndex: Int) {
        try {
            AudioPlayback.playOneShot(SampleLoader.decode(contentResolver, entry.uri), context = this)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't play ${entry.displayName}: ${e.message}", Toast.LENGTH_LONG).show()
        }

        val position = highlightedPosition
        if (position !in project.song.positions.indices) return

        val existingPhraseId = project.song.positions[position][recordTrack]
        val phraseId = existingPhraseId ?: run {
            val id = project.nextPhraseId()
            project.putPhrase(id, Phrase.empty())
            project.setSongSlot(position, recordTrack, id)
            id
        }
        val phrase = project.phrases[phraseId] ?: Phrase.empty()
        val steps = phrase.steps.toMutableList()
        val stepIndex = lastLiveStep.coerceIn(0, steps.size - 1)
        steps[stepIndex] = Step(instrument = instrumentIndex)
        project.putPhrase(phraseId, Phrase(steps))
        refreshSongGrid()
    }

    private fun toggleAudioRecording() {
        if (audioRecorder.isRecording) {
            val wav = audioRecorder.stop()
            recordAudioButton.text = "Record Audio"
            samplerPanel.load(wav, "recording-${System.currentTimeMillis()}.wav")
            Toast.makeText(this, "Recording loaded into Sampler", Toast.LENGTH_SHORT).show()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioRecording()
        } else {
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAudioRecording() {
        audioRecorder.start()
        recordAudioButton.text = "Stop Recording"
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
            sequencer = Sequencer(this, library.all())
            sequencer.onPositionChanged = { position, step -> runOnUiThread { highlightPosition(position, step) } }
            sequencer.start(project.song, project.phrases, project.bpm)
            playButton.text = "Stop"
        }
    }

    // --- Menu (M) -------------------------------------------------------------

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(arrayOf("Samples", "Sounds", "Plugins", "Theme", "Undo", "Redo", "Save Project", "Load Project", "New Project")) { _, which ->
                when (which) {
                    0 -> openSamples.launch(arrayOf("audio/*"))
                    1 -> startActivity(Intent(this, SoundLibraryActivity::class.java))
                    2 -> showPluginsDialog()
                    3 -> showThemeDialog()
                    4 -> { project.undo(); refreshSongGrid() }
                    5 -> { project.redo(); refreshSongGrid() }
                    6 -> saveProjectLauncher.launch(suggestedProjectFileName())
                    7 -> loadProjectLauncher.launch(arrayOf("application/json"))
                    8 -> confirmNewProject()
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

    private fun showThemeDialog() {
        ThemeMenu.show(
            context = this,
            onPickPicture = { pickBackgroundImage.launch(arrayOf("image/*")) },
            onPickVideo = { pickBackgroundVideo.launch(arrayOf("video/*")) },
            onRecreate = { recreate() },
        )
    }

    private fun suggestedProjectFileName(): String = "sai-project-${System.currentTimeMillis()}.json"

    private fun saveProjectTo(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)!!.use { out -> out.write(project.exportProjectJson().toByteArray()) }
            Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadProjectFrom(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            project.importProjectJson(raw)
            refreshSongGrid()
            Toast.makeText(this, "Project loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmNewProject() {
        AlertDialog.Builder(this)
            .setTitle("New Project")
            .setMessage("This clears the current song and all phrases (your sample library is kept). Continue?")
            .setPositiveButton("New Project") { _, _ ->
                project.resetProject()
                refreshSongGrid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Nav (N) ----------------------------------------------------------------

    private fun showNav() {
        AlertDialog.Builder(this)
            .setTitle("Navigate")
            .setItems(arrayOf("Back")) { _, which ->
                when (which) {
                    0 -> onBackPressedDispatcher.onBackPressed()
                }
            }
            .show()
    }

    // --- Expand (E) ---------------------------------------------------------------

    private fun showExpand() {
        AlertDialog.Builder(this)
            .setTitle("Expand")
            .setItems(arrayOf("Sampler Full Screen", "Synth Full Screen", "Tracker Full Screen", "Split View")) { _, which ->
                when (which) {
                    0 -> { viewMode = ViewMode.SAMPLER_FULL; applyViewMode() }
                    1 -> { viewMode = ViewMode.SYNTH_FULL; applyViewMode() }
                    2 -> { viewMode = ViewMode.TRACKER_FULL; applyViewMode() }
                    3 -> showSplitView()
                }
            }
            .show()
    }

    companion object {
        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
        )
    }
}
