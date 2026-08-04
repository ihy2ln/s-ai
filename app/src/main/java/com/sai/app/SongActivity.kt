package com.sai.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class SongActivity : ComponentActivity() {

    private lateinit var project: TrackerProject
    private lateinit var library: SampleLibrary
    private lateinit var sequencer: Sequencer

    private lateinit var rows: LinearLayout
    private lateinit var bpmLabel: TextView
    private lateinit var playButton: Button
    private lateinit var statusText: TextView

    private var highlightedPosition = -1
    private val rowViews = mutableListOf<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        project = TrackerProject(this)
        library = SampleLibrary(this)
        sequencer = Sequencer(contentResolver, library.all())
        sequencer.onPositionChanged = { position, step ->
            runOnUiThread { highlightPosition(position, step) }
        }

        setContentView(buildUi())
        refresh()
    }

    override fun onPause() {
        super.onPause()
        sequencer.stop()
        playButton.text = "Play"
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()

        val title = TextView(this).apply {
            text = "SONG"
            setTextColor(Color.CYAN)
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }

        bpmLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setOnClickListener { editBpm() }
        }

        playButton = Button(this).apply {
            text = "Play"
            setOnClickListener { togglePlayback() }
        }

        statusText = TextView(this).apply {
            setTextColor(Color.rgb(90, 200, 200))
            typeface = Typeface.MONOSPACE
        }

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

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(title)
            addView(transport)
            addView(header)
            addView(
                ScrollView(this@SongActivity).apply { addView(rows) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun headerCell(text: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(120, 140, 160))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun refresh() {
        bpmLabel.text = " BPM %d ".format(project.bpm)
        rows.removeAllViews()
        rowViews.clear()
        for (position in project.song.positions.indices) {
            val row = songRow(position)
            rowViews.add(row)
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
                typeface = Typeface.MONOSPACE
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
                    typeface = Typeface.MONOSPACE
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
        if (highlightedPosition in rowViews.indices) {
            rowViews[highlightedPosition].setBackgroundColor(Color.TRANSPARENT)
        }
        if (position in rowViews.indices) {
            rowViews[position].setBackgroundColor(Color.rgb(0, 50, 55))
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
                            project.setSongSlot(position, track, id)
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
                            refresh()
                        }
                    }
                }
                .show()
        }
    }

    private fun promptAssignExisting(position: Int, track: Int) {
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this)
            .setTitle("Phrase number")
            .setView(input)
            .setPositiveButton("Assign") { _, _ ->
                val id = input.text.toString().toIntOrNull()
                if (id != null) {
                    project.setSongSlot(position, track, id)
                    refresh()
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
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(project.bpm.toString())
        }
        AlertDialog.Builder(this)
            .setTitle("BPM")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toIntOrNull()?.coerceIn(20, 300)
                if (value != null) {
                    project.bpm = value
                    refresh()
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
            sequencer.onPositionChanged = { position, step ->
                runOnUiThread { highlightPosition(position, step) }
            }
            sequencer.start(project.song, project.phrases, project.bpm)
            playButton.text = "Stop"
        }
    }
}
