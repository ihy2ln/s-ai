package com.sai.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.sai.core.tracker.Arrangement
import com.sai.core.tracker.ClipKind
import com.sai.core.tracker.PlaylistClip

/** Arrangement timeline: pattern clips replace sequential song walk; audio clips play as tape. */
class PlaylistActivity : ComponentActivity() {

    private lateinit var lanesColumn: LinearLayout
    private lateinit var headerRow: LinearLayout
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var playheadViews = listOf<TextView>()
    private var stepWidthPx = 0
    private var visibleSteps = 64

    private val tick = object : Runnable {
        override fun run() {
            refreshPlayhead()
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(AppBackground.wrap(this, buildUi()))
        rebuildTimeline()
    }

    override fun onResume() {
        super.onResume()
        rebuildTimeline()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (10 * density).toInt()
        stepWidthPx = (18 * density).toInt()

        val title = Ui.screenTitle(this, "PLAYLIST")
        val addPattern = Ui.compactButton(this, "+ PAT") { addPatternClip() }
        val addAudio = Ui.compactButton(this, "+ AUD") { addAudioClip() }
        val clear = Ui.compactButton(this, "Clear") { confirmClear() }
        val titleRow = Ui.headerBar(this) {
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(addPattern)
            addView(addAudio)
            addView(clear)
            addView(PillButton.create(this@PlaylistActivity, "N") { NavMenu.show(this@PlaylistActivity) })
        }

        status = TextView(this).apply {
            setTextColor(AppTheme.textSecondary)
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }

        headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lanesColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val timeline = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerRow)
            addView(lanesColumn)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(AppTheme.canvas)
            addView(titleRow)
            addView(status)
            addView(
                HorizontalScrollView(this@PlaylistActivity).apply {
                    isHorizontalScrollBarEnabled = true
                    addView(
                        ScrollView(this@PlaylistActivity).apply {
                            addView(timeline)
                        },
                    )
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun rebuildTimeline() {
        val project = TrackerProjectStore.get(this)
        val clips = PlaylistStore.load(this)
        val total = Arrangement.totalSteps(clips, project.song) { project.patternLength(it) }
        visibleSteps = maxOf(64, total + 16, clips.maxOfOrNull { it.endStep + 8 } ?: 0)
        status.text = if (clips.isEmpty()) {
            "Empty — song grid 00–1F plays as usual. Add PAT / AUD clips to arrange."
        } else {
            val patterns = clips.count { it.kind == ClipKind.PATTERN }
            val audio = clips.count { it.kind == ClipKind.AUDIO }
            "$patterns pattern  $audio audio  ${total} steps"
        }

        headerRow.removeAllViews()
        val headers = mutableListOf<TextView>()
        for (step in 0 until visibleSteps) {
            val cell = TextView(this).apply {
                text = if (step % 4 == 0) "%02X".format(step) else ""
                gravity = Gravity.CENTER
                textSize = 8f
                setTextColor(AppTheme.textMuted)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(stepWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            headers.add(cell)
            headerRow.addView(cell)
        }
        playheadViews = headers

        lanesColumn.removeAllViews()
        val density = resources.displayMetrics.density
        val laneH = (36 * density).toInt()
        for (lane in 0 until Arrangement.LANES) {
            val row = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(stepWidthPx * visibleSteps, laneH).apply {
                    setMargins(0, (2 * density).toInt(), 0, 0)
                }
                setBackgroundColor(AppTheme.surfaceMuted)
                setOnClickListener { promptAddAt(lane, Arrangement.appendStart(clips)) }
            }
            val label = TextView(this).apply {
                text = " L${lane + 1}"
                setTextColor(AppTheme.textMuted)
                textSize = 9f
                typeface = Typeface.MONOSPACE
            }
            row.addView(label)
            for (clip in clips.filter { it.lane == lane }) {
                row.addView(clipView(clip, laneH))
            }
            lanesColumn.addView(row)
        }
        refreshPlayhead()
    }

    private fun clipView(clip: PlaylistClip, height: Int): TextView {
        val label = when (clip.kind) {
            ClipKind.PATTERN -> "P%02X".format(clip.pattern ?: 0)
            ClipKind.AUDIO -> {
                val name = SampleLibrary(this).get(clip.sampleId ?: -1)?.displayName ?: "AUD"
                name.take(8)
            }
        }
        val color = when {
            clip.muted -> AppTheme.textMuted
            clip.kind == ClipKind.PATTERN -> Color.rgb(46, 96, 148)
            else -> Color.rgb(148, 88, 42)
        }
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(AppTheme.textPrimary)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(color)
            layoutParams = FrameLayout.LayoutParams(clip.length * stepWidthPx, height).apply {
                leftMargin = clip.startStep * stepWidthPx
            }
            setOnClickListener { editClip(clip) }
        }
    }

    private fun refreshPlayhead() {
        val step = ArrangementClock.globalStep
        for ((index, view) in playheadViews.withIndex()) {
            view.setBackgroundColor(if (index == step) AppTheme.gold else Color.TRANSPARENT)
        }
    }

    private fun addPatternClip() {
        val project = TrackerProjectStore.get(this)
        val labels = Array(project.song.positions.size) { "%02X".format(it) }
        AlertDialog.Builder(this)
            .setTitle("Pattern")
            .setItems(labels) { _, which ->
                val clips = PlaylistStore.load(this)
                val length = project.patternLength(which)
                PlaylistStore.add(
                    this,
                    PlaylistClip(
                        id = Arrangement.nextId(clips),
                        kind = ClipKind.PATTERN,
                        lane = 0,
                        startStep = Arrangement.appendStart(clips),
                        lengthSteps = length,
                        pattern = which,
                    ),
                )
                rebuildTimeline()
            }
            .show()
    }

    private fun addAudioClip() {
        val entries = SampleLibrary(this).all()
        if (entries.isEmpty()) {
            Toast.makeText(this, "Import a sample first", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Audio clip")
            .setItems(entries.map { it.displayName }.toTypedArray()) { _, which ->
                val entry = entries[which]
                val project = TrackerProjectStore.get(this)
                val clips = PlaylistStore.load(this)
                val wav = try {
                    SampleLoader.decode(contentResolver, entry.uri)
                } catch (e: Exception) {
                    Toast.makeText(this, "Couldn't read ${entry.displayName}", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                PlaylistStore.add(
                    this,
                    PlaylistClip(
                        id = Arrangement.nextId(clips),
                        kind = ClipKind.AUDIO,
                        lane = 1,
                        startStep = Arrangement.appendStart(clips),
                        lengthSteps = Arrangement.lengthStepsFor(wav.frameCount, wav.sampleRate, project.bpm),
                        sampleId = entry.id,
                    ),
                )
                rebuildTimeline()
            }
            .show()
    }

    private fun promptAddAt(lane: Int, start: Int) {
        AlertDialog.Builder(this)
            .setTitle("Lane ${lane + 1}")
            .setItems(arrayOf("Add pattern here", "Add audio here")) { _, which ->
                when (which) {
                    0 -> addPatternAt(lane, start)
                    1 -> addAudioAt(lane, start)
                }
            }
            .show()
    }

    private fun addPatternAt(lane: Int, start: Int) {
        val project = TrackerProjectStore.get(this)
        val labels = Array(project.song.positions.size) { "%02X".format(it) }
        AlertDialog.Builder(this)
            .setTitle("Pattern")
            .setItems(labels) { _, which ->
                val clips = PlaylistStore.load(this)
                PlaylistStore.add(
                    this,
                    PlaylistClip(
                        id = Arrangement.nextId(clips),
                        kind = ClipKind.PATTERN,
                        lane = lane,
                        startStep = start,
                        lengthSteps = project.patternLength(which),
                        pattern = which,
                    ),
                )
                rebuildTimeline()
            }
            .show()
    }

    private fun addAudioAt(lane: Int, start: Int) {
        val entries = SampleLibrary(this).all()
        if (entries.isEmpty()) {
            Toast.makeText(this, "Import a sample first", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Audio clip")
            .setItems(entries.map { it.displayName }.toTypedArray()) { _, which ->
                val entry = entries[which]
                val project = TrackerProjectStore.get(this)
                val clips = PlaylistStore.load(this)
                val wav = try {
                    SampleLoader.decode(contentResolver, entry.uri)
                } catch (e: Exception) {
                    return@setItems
                }
                PlaylistStore.add(
                    this,
                    PlaylistClip(
                        id = Arrangement.nextId(clips),
                        kind = ClipKind.AUDIO,
                        lane = lane,
                        startStep = start,
                        lengthSteps = Arrangement.lengthStepsFor(wav.frameCount, wav.sampleRate, project.bpm),
                        sampleId = entry.id,
                    ),
                )
                rebuildTimeline()
            }
            .show()
    }

    private fun editClip(clip: PlaylistClip) {
        val muteLabel = if (clip.muted) "Unmute" else "Mute"
        AlertDialog.Builder(this)
            .setTitle(clipTitle(clip))
            .setItems(arrayOf(muteLabel, "Nudge −4", "Nudge +4", "Length", "Lane", "Delete")) { _, which ->
                when (which) {
                    0 -> PlaylistStore.update(this, clip.copy(muted = !clip.muted))
                    1 -> PlaylistStore.update(this, clip.copy(startStep = (clip.startStep - 4).coerceAtLeast(0)))
                    2 -> PlaylistStore.update(this, clip.copy(startStep = clip.startStep + 4))
                    3 -> editLength(clip)
                    4 -> editLane(clip)
                    5 -> PlaylistStore.remove(this, clip.id)
                }
                rebuildTimeline()
            }
            .show()
    }

    private fun editLength(clip: PlaylistClip) {
        val choices = listOf(8, 16, 32, 64)
        AlertDialog.Builder(this)
            .setTitle("Length (16ths)")
            .setItems(choices.map { it.toString() }.toTypedArray()) { _, which ->
                PlaylistStore.update(this, clip.copy(lengthSteps = choices[which]))
                rebuildTimeline()
            }
            .show()
    }

    private fun editLane(clip: PlaylistClip) {
        val labels = Array(Arrangement.LANES) { "Lane ${it + 1}" }
        AlertDialog.Builder(this)
            .setTitle("Lane")
            .setItems(labels) { _, which ->
                PlaylistStore.update(this, clip.copy(lane = which))
                rebuildTimeline()
            }
            .show()
    }

    private fun clipTitle(clip: PlaylistClip): String = when (clip.kind) {
        ClipKind.PATTERN -> "Pattern %02X".format(clip.pattern ?: 0)
        ClipKind.AUDIO -> SampleLibrary(this).get(clip.sampleId ?: -1)?.displayName ?: "Audio"
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear playlist?")
            .setMessage("Song grid playback comes back. Clips are removed.")
            .setPositiveButton("Clear") { _, _ ->
                PlaylistStore.clear(this)
                rebuildTimeline()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
