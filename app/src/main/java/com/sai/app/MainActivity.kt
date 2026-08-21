package com.sai.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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

    private lateinit var library: SampleLibrary
    private lateinit var project: TrackerProject
    private lateinit var sequencer: Sequencer

    private lateinit var rootView: LinearLayout
    private lateinit var modulesColumn: ModuleStackView
    private lateinit var modulesScroll: ModulesScrollView
    private lateinit var edgeScrollBar: EdgeScrollBar
    private lateinit var moduleEntries: MutableList<ModuleEntry>
    private var fullScreenModule: ModuleType? = null
    private var isDraggingModuleHandle = false
    private var lastModuleScrollHeight = 0
    private var keepUserModuleHeights = false

    // Sampler module (null when that module isn't on screen)
    private var samplerPanel: SamplerPanelView? = null
    private var sampleListContainer: LinearLayout? = null
    private var recordAudioButton: Button? = null

    // Synth module
    private var synthPanel: SynthPanelView? = null

    // Step sequencer module
    private var stepSequencerPanel: ChannelRackPanelView? = null

    // Tracker module (grid only - transport lives in the top bar, see below)
    private var statusText: TextView? = null
    private var songRows: LinearLayout? = null

    // Global transport (top bar) - present on every screen regardless of which modules are shown
    private lateinit var projectTitleLabel: TextView
    private lateinit var bpmLabel: TextView
    private lateinit var playButton: TransportShapeButtonView
    private lateinit var recordArmButton: TransportShapeButtonView
    private lateinit var tempoDot: ImageView
    private lateinit var tempoBouncer: TempoBouncer
    private val tapTimestamps = mutableListOf<Long>()

    private var highlightedPosition = -1
    private var lastLiveStep = 0
    private val songRowViews = mutableListOf<LinearLayout>()
    private val chokeButtons = mutableMapOf<ModuleType, Button>()

    private val audioRecorder = AudioRecorder()
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
                synthPanel?.load(SampleLoader.decode(contentResolver, uri), name)
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
        sequencer = Sequencer(this, library.all(), ModuleLayoutStore.isChokeEnabled(this, ModuleType.TRACKER))
        sequencer.onPositionChanged = { position, step ->
            runOnUiThread {
                highlightPosition(position, step)
                if (step % 4 == 0) tempoBouncer.pulseOnce()
            }
        }

        setContentView(AppBackground.wrap(this, buildUi()))
        updateRouteLabel()
        (getSystemService(AUDIO_SERVICE) as AudioManager).registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    override fun onResume() {
        super.onResume()
        refreshSampleList()
        tempoBouncer.setBpm(project.bpm)
        if (!sequencer.isRunning) tempoBouncer.startIdle()
        updateRouteLabel()
    }

    override fun onPause() {
        super.onPause()
        sequencer.stop()
        updatePlayButtonAppearance()
        stepSequencerPanel?.setPlayhead(-1)
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
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setPadding(0, 0, (4 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams((100 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { editProjectName() }
        }
        projectTitleLabel = title
        refreshProjectTitle()
        routeLabel = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, (4 * density).toInt(), 0)
        }

        val bpmLabelView = TextView(this).apply {
            setTextColor(Color.rgb(170, 180, 190))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
            isClickable = true
            text = "BPM %d".format(project.bpm)
            setPadding(0, (2 * density).toInt(), (4 * density).toInt(), 0)
        }
        bpmLabel = bpmLabelView
        wireBpmScrub(bpmLabelView)

        val tempoDotView = ImageView(this).apply {
            setImageResource(R.drawable.tempo_dot)
            layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), (12 * density).toInt()).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        tempoDot = tempoDotView
        tempoBouncer = TempoBouncer(tempoDotView, 8 * density)

        val tempoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bpmLabelView)
            addView(tempoDotView)
        }

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams((100 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(title)
            addView(tempoRow)
        }

        val tapButton = TransportShapeButton.createTempo(this) { tapTempo() }

        val playButtonView = TransportShapeButton.create(
            this,
            "Play",
            ShapeIconView.Shape.TRIANGLE,
            TransportShapeButton.PLAY_GREEN,
            big = true,
        ) { togglePlayback() }
        playButton = playButtonView

        val recordArmButtonView = TransportShapeButton.create(
            this,
            "Record",
            ShapeIconView.Shape.CIRCLE,
            TransportShapeButton.RECORD_RED,
        ) {
            recordArmed = !recordArmed
            updateRecordButton()
            if (recordArmed) Toast.makeText(this@MainActivity, "Live record armed: tap a sample to punch it in while playing", Toast.LENGTH_LONG).show()
        }
        recordArmButton = recordArmButtonView
        updateRecordButton()

        val projectSoundButton = TransportShapeButton.create(
            this,
            "Sound",
            ShapeIconView.Shape.SQUARE,
            TransportShapeButton.EDIT_BLUE,
        ) { ProjectSoundMenu.show(this) }

        val transportControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(playButtonView)
            addView(recordArmButtonView)
            addView(projectSoundButton)
            addView(tapButton)
        }

        val transportScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(transportControls)
                    addView(routeLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins((8 * density).toInt(), 0, 0, 0)
                    })
                    addView(PillButton.create(this@MainActivity, "E") { showExpand() })
                    addView(PillButton.create(this@MainActivity, "N") { showNav() })
                    addView(PillButton.create(this@MainActivity, "MX") { EffectsMenu.show(this@MainActivity, samplerEffectsTarget()) })
                    addView(PillButton.create(this@MainActivity, "P") { showProjectMenu() })
                    addView(PillButton.create(this@MainActivity, "M") { showMenu() })
                },
            )
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (4 * density).toInt())
            addView(titleColumn)
            addView(transportScroll)
        }

        moduleEntries = ModuleLayoutStore.load(this)
        modulesColumn = ModuleStackView(this).apply {
            onResizeStart = {
                isDraggingModuleHandle = true
            }
            onResizeEnd = {
                isDraggingModuleHandle = false
                keepUserModuleHeights = true
                syncEntriesFromDisplayedHeights()
                ModuleLayoutStore.save(this@MainActivity, moduleEntries)
            }
        }

        modulesScroll = ModulesScrollView(this).apply {
            isFillViewport = false
            addView(modulesColumn, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT))
            viewTreeObserver.addOnGlobalLayoutListener {
                if (isDraggingModuleHandle || keepUserModuleHeights) return@addOnGlobalLayoutListener
                val h = height
                if (h > 0 && h != lastModuleScrollHeight) {
                    lastModuleScrollHeight = h
                    fitModulesToScreen()
                }
                if (::edgeScrollBar.isInitialized) edgeScrollBar.invalidate()
            }
        }

        edgeScrollBar = EdgeScrollBar(this).apply { scrollTarget = modulesScroll }

        val modulesArea = FrameLayout(this).apply {
            addView(modulesScroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(
                edgeScrollBar,
                FrameLayout.LayoutParams((22 * density).toInt(), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END),
            )
        }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, 0, pad)
            setBackgroundColor(Color.rgb(18, 18, 20))
            addView(headerRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = pad
            })
            addView(modulesArea, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            isLongClickable = true
            setOnLongClickListener { showNav(); true }
        }

        rebuildModulesColumn()
        return rootView
    }

    private fun updatePlayButtonAppearance() {
        if (sequencer.isRunning) {
            playButton.setAppearance(ShapeIconView.Shape.SQUARE, TransportShapeButton.STOP_WHITE, "Stop")
        } else {
            playButton.setAppearance(ShapeIconView.Shape.TRIANGLE, TransportShapeButton.PLAY_GREEN, "Play")
        }
    }

    private fun updateRecordButton() {
        recordArmButton.setAppearance(
            ShapeIconView.Shape.CIRCLE,
            if (recordArmed) TransportShapeButton.RECORD_ARMED else TransportShapeButton.RECORD_RED,
            "Record",
        )
    }

    private fun refreshProjectTitle() {
        projectTitleLabel.text = project.name
        projectTitleLabel.isSelected = true
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
            .setPositiveButton("Save") { _, _ ->
                project.name = input.text.toString()
                refreshProjectTitle()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProjectMenu() {
        ProjectMenu.show(
            this,
            ProjectMenu.Actions(
                onRename = { editProjectName() },
                onSave = { saveProjectLauncher.launch(suggestedProjectFileName()) },
                onLoad = { loadProjectLauncher.launch(arrayOf("application/json")) },
                onNew = { confirmNewProject() },
                onUndo = { project.undo(); refreshSongGrid() },
                onRedo = { project.redo(); refreshSongGrid() },
            ),
        )
    }

    /** BPM is adjustable three ways: tap opens the type-a-number dialog ([editBpm]), a vertical
     *  drag scrubs it up/down, and [tapTempo] derives it from tap intervals. */
    private fun wireBpmScrub(label: TextView) {
        var startY = 0f
        var startBpm = project.bpm
        var dragged = false
        label.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startBpm = project.bpm
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = startY - event.rawY
                    if (kotlin.math.abs(deltaY) > 8f) dragged = true
                    if (dragged) setBpmValue(startBpm + (deltaY / 6f).toInt())
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) v.performClick()
                    true
                }
                else -> false
            }
        }
        label.setOnClickListener { editBpm() }
    }

    private fun tapTempo() {
        val now = System.currentTimeMillis()
        if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > 2000) {
            tapTimestamps.clear()
        }
        tapTimestamps.add(now)
        while (tapTimestamps.size > 5) tapTimestamps.removeAt(0)
        if (tapTimestamps.size >= 2) {
            val avgMs = tapTimestamps.zipWithNext { a, b -> b - a }.average()
            if (avgMs > 0) setBpmValue((60000.0 / avgMs).toInt())
        }
    }

    private fun setBpmValue(newBpm: Int) {
        project.bpm = newBpm.coerceIn(20, 300)
        refreshSongGrid()
    }

    /** Rebuilds every module (and the resize handles between them) from [moduleEntries] /
     *  [fullScreenModule], then repopulates the sample list and song grid into the fresh views.
     *  Note: this recreates the Sampler/Synth panels, so a loaded-but-unsaved sound is cleared -
     *  an accepted trade-off for keeping add/remove/reorder simple and reliable. */
    private fun rebuildModulesColumn() {
        modulesColumn.removeAllViews()
        chokeButtons.clear()
        stepSequencerPanel = null
        val density = resources.displayMetrics.density

        val full = fullScreenModule
        if (full != null && moduleEntries.any { it.type == full }) {
            modulesColumn.addView(
                buildModuleWrapper(full),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT),
            )
        } else {
            fullScreenModule = null
            for (entry in moduleEntries) {
                val heightPx = (entry.heightDp * density).toInt()
                modulesColumn.addView(buildModuleWrapper(entry.type), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx))
                modulesColumn.addView(ResizeHandleView(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (ModuleLayoutFit.HANDLE_HEIGHT_DP * density).toInt()))
            }
        }

        refreshSampleList()
        refreshSongGrid()
        if (keepUserModuleHeights) {
            applyStoredModuleHeights()
        } else {
            fitModulesToScreen()
        }
    }

    private fun fitModulesToScreen() {
        if (!::modulesScroll.isInitialized || isDraggingModuleHandle || keepUserModuleHeights) return
        ModuleLayoutFit.redistribute(
            scroll = modulesScroll,
            column = modulesColumn,
            entries = moduleEntries,
            density = resources.displayMetrics.density,
            minHeightDp = MIN_MODULE_HEIGHT_DP,
            isFullScreen = fullScreenModule != null,
            orientation = resources.configuration.orientation,
        )
        syncEntriesFromDisplayedHeights()
    }

    private fun applyStoredModuleHeights() {
        ModuleLayoutFit.applyStoredHeights(modulesColumn, moduleEntries, resources.displayMetrics.density)
    }

    /** Read current on-screen pixel heights back into [moduleEntries] so drag starts from what the user sees. */
    private fun syncEntriesFromDisplayedHeights() {
        val density = resources.displayMetrics.density
        for (index in moduleEntries.indices) {
            val wrapper = modulesColumn.getChildAt(index * 2) ?: continue
            val heightPx = if (wrapper.height > 0) wrapper.height else wrapper.layoutParams.height
            if (heightPx > 0) {
                moduleEntries[index].heightDp = heightPx / density
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fitModulesToScreen()
    }

    private fun buildModuleWrapper(type: ModuleType): LinearLayout {
        val titleText = TextView(this).apply {
            text = type.label
            setTextColor(AppTheme.accentColor(this@MainActivity))
            textSize = 16f
        }
        val upButton = Button(this).apply { text = "▲"; setOnClickListener { moveModule(type, -1) } }
        val downButton = Button(this).apply { text = "▼"; setOnClickListener { moveModule(type, 1) } }
        val removeButton = Button(this).apply { text = "−"; setOnClickListener { removeModule(type) } }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            when (type) {
                ModuleType.SAMPLER -> {
                    val recordButton = Button(this@MainActivity).apply {
                        text = "Record Audio"
                        setOnClickListener { toggleAudioRecording() }
                    }
                    recordAudioButton = recordButton
                    addView(recordButton)
                    addView(Button(this@MainActivity).apply { text = "+"; setOnClickListener { openSamples.launch(arrayOf("audio/*")) } })
                }
                ModuleType.SYNTH -> {
                    addView(Button(this@MainActivity).apply { text = "+"; setOnClickListener { openSynthSample.launch(arrayOf("audio/*")) } })
                }
                ModuleType.TRACKER -> {
                    addView(Button(this@MainActivity).apply { text = "+"; setOnClickListener { openSamples.launch(arrayOf("audio/*")) } })
                }
                ModuleType.STEP_SEQUENCER -> {
                    addView(Button(this@MainActivity).apply { text = "+"; setOnClickListener { openSamples.launch(arrayOf("audio/*")) } })
                }
            }
            addView(buildChokeButton(type))
            addView(upButton)
            addView(downButton)
            addView(removeButton)
        }

        val content = when (type) {
            ModuleType.SAMPLER -> buildSamplerContent()
            ModuleType.SYNTH -> buildSynthContent()
            ModuleType.TRACKER -> buildTrackerContent()
            ModuleType.STEP_SEQUENCER -> ChannelRackPanelView(this).also { stepSequencerPanel = it }
        }

        val touchPanel = ModuleTouchPanel(this).apply {
            addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow)
            addView(touchPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    /** "CUT" toggles Cut Itself (mono/poly) for this module - see [ModuleLayoutStore.chokeKey]:
     *  Tracker and Step Sequencer share one value since they play back the same song data
     *  through one engine, so toggling either one's button updates both in place. */
    private fun buildChokeButton(type: ModuleType): Button {
        val button = Button(this)
        styleChokeButton(button, ModuleLayoutStore.isChokeEnabled(this, type))
        button.setOnClickListener {
            val newValue = !ModuleLayoutStore.isChokeEnabled(this, type)
            ModuleLayoutStore.setChokeEnabled(this, type, newValue)
            val key = ModuleLayoutStore.chokeKey(type)
            for (candidate in ModuleType.values()) {
                if (ModuleLayoutStore.chokeKey(candidate) == key) {
                    chokeButtons[candidate]?.let { styleChokeButton(it, newValue) }
                }
            }
        }
        chokeButtons[type] = button
        return button
    }

    private fun styleChokeButton(button: Button, enabled: Boolean) {
        button.text = if (enabled) "MONO" else "POLY"
        button.setTextColor(if (enabled) Color.BLACK else Color.WHITE)
        button.setBackgroundColor(if (enabled) AppTheme.accentColor(this) else Color.DKGRAY)
    }

    private fun moveModule(type: ModuleType, delta: Int) {
        val index = moduleEntries.indexOfFirst { it.type == type }
        if (index < 0) return
        val newIndex = (index + delta).coerceIn(0, moduleEntries.size - 1)
        if (newIndex == index) return
        val entry = moduleEntries.removeAt(index)
        moduleEntries.add(newIndex, entry)
        ModuleLayoutStore.save(this, moduleEntries)
        rebuildModulesColumn()
    }

    private fun removeModule(type: ModuleType) {
        AlertDialog.Builder(this)
            .setTitle("Remove ${type.label}?")
            .setMessage("You can add it back from Menu > Add Module. Your samples and project data are kept either way.")
            .setPositiveButton("Remove") { _, _ ->
                moduleEntries.removeAll { it.type == type }
                ModuleLayoutStore.save(this, moduleEntries)
                rebuildModulesColumn()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddModuleDialog() {
        val missing = ModuleType.values().filter { type -> moduleEntries.none { it.type == type } }
        if (missing.isEmpty()) {
            Toast.makeText(this, "All modules are already on screen", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = missing.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Add Module")
            .setItems(labels) { _, which ->
                val type = missing[which]
                moduleEntries.add(ModuleEntry(type, ModuleLayoutStore.defaultHeight(type)))
                ModuleLayoutStore.save(this, moduleEntries)
                rebuildModulesColumn()
            }
            .show()
    }

    private fun buildSamplerContent(): LinearLayout {
        val density = resources.displayMetrics.density

        val panel = SamplerPanelView(this).apply {
            onSaveSlices = { sourceName, slices ->
                val saved = SliceExporter.saveToLibrary(this@MainActivity, sourceName, slices)
                Toast.makeText(this@MainActivity, "Saved ${saved.size} slices to your sample library", Toast.LENGTH_LONG).show()
                refreshSampleList()
            }
        }
        samplerPanel = panel

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sampleListContainer = listContainer

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(panel)
            addView(
                ScrollView(this@MainActivity).apply {
                    isNestedScrollingEnabled = false
                    addView(listContainer)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (100 * density).toInt()),
            )
        }
    }

    private fun buildSynthContent(): LinearLayout {
        val panel = SynthPanelView(this).apply {
            onSaveToLibrary = { sourceName, wav ->
                val saved = SliceExporter.saveToLibrary(this@MainActivity, sourceName, listOf(wav), SoundCategory.SYNTH)
                Toast.makeText(this@MainActivity, "Saved ${saved.size} to your sample library", Toast.LENGTH_LONG).show()
                refreshSampleList()
            }
        }
        synthPanel = panel

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(panel)
        }
    }

    private fun buildTrackerContent(): LinearLayout {
        val statusTextView = TextView(this).apply { setTextColor(Color.rgb(90, 200, 200)) }
        statusText = statusTextView

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(headerCell("  "))
            for (track in 0 until project.song.trackCount) headerCell((track + 1).toString())
        }

        val songRowsView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        songRows = songRowsView

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusTextView)
            addView(header)
            addView(
                ScrollView(this@MainActivity).apply {
                    isNestedScrollingEnabled = false
                    addView(songRowsView)
                },
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
        val container = sampleListContainer ?: return
        container.removeAllViews()
        val entries = library.all()
        if (entries.isEmpty()) {
            container.addView(label("No samples yet. Tap M > Samples or Sounds."))
            return
        }
        for ((index, entry) in entries.withIndex()) {
            container.addView(sampleRow(entry, index, PALETTE[index % PALETTE.size]))
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
        val panel = samplerPanel
        if (panel == null) {
            Toast.makeText(this, "Add the Sampler module first (Menu > Add Module)", Toast.LENGTH_LONG).show()
            return
        }
        val wav = try {
            SampleLoader.decode(contentResolver, entry.uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load ${entry.displayName}: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        panel.load(wav, entry.displayName)
    }

    private fun samplerEffectsTarget() = EffectsTarget(
        getWav = { samplerPanel?.currentWav() },
        getName = { samplerPanel?.currentSourceName() ?: "Sampler" },
        onApplied = { processed -> samplerPanel?.let { it.load(processed, it.currentSourceName()) } },
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
        bpmLabel.text = "BPM %d".format(project.bpm)
        refreshProjectTitle()
        tempoBouncer.setBpm(project.bpm)
        if (!sequencer.isRunning) tempoBouncer.startIdle()

        val rows = songRows ?: return
        rows.removeAllViews()
        songRowViews.clear()
        for (position in project.song.positions.indices) {
            val row = songRow(position)
            songRowViews.add(row)
            rows.addView(row)
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
        statusText?.text = " %02X:%X".format(position, step)
        stepSequencerPanel?.setPlayhead(step)
    }

    /** Records a live "session" hit: plays the sample immediately and writes it into the phrase
     *  at the sequencer's current position/step, similar to punching in a pad hit while playing. */
    private fun recordLiveHit(entry: SampleEntry, instrumentIndex: Int) {
        try {
            val choke = ModuleLayoutStore.isChokeEnabled(this, ModuleType.SAMPLER)
            AudioPlayback.playOneShot(SampleLoader.decode(contentResolver, entry.uri), context = this, chokeGroup = if (choke) "sampler" else null)
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
            recordAudioButton?.text = "Record Audio"
            val panel = samplerPanel
            if (panel == null) {
                Toast.makeText(this, "Add the Sampler module first (Menu > Add Module)", Toast.LENGTH_LONG).show()
            } else {
                panel.load(wav, "recording-${System.currentTimeMillis()}.wav")
                Toast.makeText(this, "Recording loaded into Sampler", Toast.LENGTH_SHORT).show()
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioRecording()
        } else {
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAudioRecording() {
        audioRecorder.start()
        recordAudioButton?.text = "Stop Recording"
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
            updatePlayButtonAppearance()
            stepSequencerPanel?.setPlayhead(-1)
            tempoBouncer.startIdle()
        } else {
            sequencer = Sequencer(this, library.all(), ModuleLayoutStore.isChokeEnabled(this, ModuleType.TRACKER))
            sequencer.onPositionChanged = { position, step ->
                runOnUiThread {
                    highlightPosition(position, step)
                    if (step % 4 == 0) tempoBouncer.pulseOnce()
                }
            }
            sequencer.start(project.song, project.phrases, project.bpm)
            updatePlayButtonAppearance()
            tempoBouncer.stop()
        }
    }

    // --- Menu (M) -------------------------------------------------------------

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(arrayOf("Manual", "Samples", "Sounds", "Plugins", "Theme", "Add Module")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, ManualActivity::class.java))
                    1 -> openSamples.launch(arrayOf("audio/*"))
                    2 -> startActivity(Intent(this, SoundLibraryActivity::class.java))
                    3 -> showPluginsDialog()
                    4 -> showThemeDialog()
                    5 -> showAddModuleDialog()
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
            stepSequencerPanel?.refreshRows()
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
                stepSequencerPanel?.refreshRows()
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
        val options = moduleEntries.map { "${it.type.label} Full Screen" } + "Split View"
        AlertDialog.Builder(this)
            .setTitle("Expand")
            .setItems(options.toTypedArray()) { _, which ->
                fullScreenModule = if (which == options.size - 1) null else moduleEntries[which].type
                rebuildModulesColumn()
            }
            .show()
    }

    companion object {
        private const val MIN_MODULE_HEIGHT_DP = 64f
        private const val MAX_MODULE_HEIGHT_DP = 1200f

        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
        )
    }
}
