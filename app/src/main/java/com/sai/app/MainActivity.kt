package com.sai.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.sai.core.tracker.Phrase

class MainActivity : ComponentActivity() {

    private lateinit var library: SampleLibrary
    private lateinit var listContainer: LinearLayout

    private val openSamples = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importSamples(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = SampleLibrary(this)

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val title = TextView(this).apply {
            text = "S.Ai"
            setTextColor(Color.WHITE)
            textSize = 28f
        }

        val trackerButton = Button(this).apply {
            text = "Open Tracker"
            setTextColor(Color.BLACK)
            background = pillBackground(Color.rgb(38, 198, 218))
            setOnClickListener { startActivity(Intent(this@MainActivity, SongActivity::class.java)) }
        }

        val importButton = Button(this).apply {
            text = "Import Samples"
            setTextColor(Color.BLACK)
            background = pillBackground(Color.rgb(230, 30, 99))
            setOnClickListener { openSamples.launch(arrayOf("audio/*")) }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(trackerButton, rowParams())
            addView(importButton, rowParams())
        }

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(Color.rgb(18, 18, 20))
                addView(title)
                addView(buttonRow)
                addView(
                    ScrollView(this@MainActivity).apply { addView(listContainer) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun rowParams(): LinearLayout.LayoutParams {
        val margin = (4 * resources.displayMetrics.density).toInt()
        return LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(margin, margin, margin, margin)
        }
    }

    private fun pillBackground(color: Int) = GradientDrawable().apply {
        cornerRadius = 24f * resources.displayMetrics.density
        setColor(color)
    }

    private fun importSamples(uris: List<Uri>) {
        val entries = uris.map { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Grant couldn't be persisted (e.g. per-app URI grant limit); the sample
                // still works this session, it just won't survive an app restart.
            }
            SampleEntry(uri, queryDisplayName(uri))
        }
        library.add(entries)
        refreshList()
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

    private fun refreshList() {
        listContainer.removeAllViews()
        val entries = library.all()
        if (entries.isEmpty()) {
            listContainer.addView(label("No samples yet. Tap Import Samples."))
            return
        }
        for ((index, entry) in entries.withIndex()) {
            listContainer.addView(sampleCard(entry, PALETTE[index % PALETTE.size]))
        }
    }

    private fun sampleCard(entry: SampleEntry, accent: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val margin = (4 * density).toInt()

        val accentStrip = View(this).apply { setBackgroundColor(accent) }
        val nameButton = Button(this).apply {
            text = entry.displayName
            setTextColor(Color.WHITE)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = null
            setOnClickListener { openSampleOptions(entry) }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(30, 30, 34))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, margin, 0, margin)
            }
            addView(accentStrip, LinearLayout.LayoutParams((6 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(nameButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun openSampleOptions(entry: SampleEntry) {
        AlertDialog.Builder(this)
            .setTitle(entry.displayName)
            .setItems(arrayOf("Slice + Sequence", "Edit")) { _, which ->
                if (which == 0) {
                    val project = TrackerProject(this)
                    val phraseId = project.nextPhraseId()
                    project.putPhrase(phraseId, Phrase.empty())
                    startActivity(
                        Intent(this, PhraseActivity::class.java)
                            .putExtra(PhraseActivity.EXTRA_PHRASE_ID, phraseId)
                            .putExtra(SampleEditorActivity.EXTRA_SAMPLE_URI, entry.uri)
                    )
                } else {
                    startActivity(Intent(this, SampleEditorActivity::class.java).putExtra(SampleEditorActivity.EXTRA_SAMPLE_URI, entry.uri))
                }
            }
            .show()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
    }

    companion object {
        private val PALETTE = intArrayOf(
            Color.rgb(230, 30, 99), Color.rgb(76, 175, 80), Color.rgb(255, 193, 7),
            Color.rgb(38, 198, 218), Color.rgb(156, 39, 176), Color.rgb(255, 87, 34),
        )
    }
}
