package com.sai.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var library: SampleLibrary
    private lateinit var listContainer: LinearLayout

    private val openSamples = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importSamples(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = SampleLibrary(this)

        val trackerButton = Button(this).apply {
            text = "Open Tracker"
            setOnClickListener { startActivity(Intent(this@MainActivity, SongActivity::class.java)) }
        }

        val importButton = Button(this).apply {
            text = "Import Samples"
            setOnClickListener { openSamples.launch(arrayOf("audio/*")) }
        }

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                addView(trackerButton)
                addView(importButton)
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
        for (entry in entries) {
            listContainer.addView(
                Button(this).apply {
                    text = entry.displayName
                    setOnClickListener {
                        startActivity(
                            Intent(this@MainActivity, SampleEditorActivity::class.java)
                                .putExtra(SampleEditorActivity.EXTRA_SAMPLE_URI, entry.uri)
                        )
                    }
                }
            )
        }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
    }
}
