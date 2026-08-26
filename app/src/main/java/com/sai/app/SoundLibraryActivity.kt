package com.sai.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class SoundLibraryActivity : ComponentActivity() {

    private lateinit var library: SampleLibrary
    private lateinit var listContainer: LinearLayout
    private lateinit var searchInput: EditText

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) promptCategoryThenImport(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = SampleLibrary(this)

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val title = TextView(this).apply {
            text = "SOUNDS"
            setTextColor(AppTheme.accentColor(this@SoundLibraryActivity))
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }

        val addButton = Button(this).apply {
            text = "Add Sounds"
            setOnClickListener { importLauncher.launch(arrayOf("audio/*")) }
        }

        searchInput = EditText(this).apply {
            hint = "Search name, category, tags"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(100, 110, 120))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(title)
            addView(addButton)
            addView(searchInput)
            addView(
                ScrollView(this@SoundLibraryActivity).apply { addView(listContainer) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(AppBackground.wrap(this, root))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val query = if (::searchInput.isInitialized) searchInput.text.toString() else ""
        val filtered = library.search(query)
        if (query.isNotBlank()) {
            listContainer.addView(
                TextView(this).apply {
                    text = "Results (${filtered.size})"
                    setTextColor(Color.rgb(120, 140, 160))
                    typeface = Typeface.MONOSPACE
                    textSize = 14f
                },
            )
            if (filtered.isEmpty()) {
                listContainer.addView(
                    TextView(this).apply {
                        text = "  (no matches)"
                        setTextColor(Color.rgb(80, 80, 85))
                    },
                )
            } else {
                for (entry in filtered) listContainer.addView(entryRow(entry))
            }
            return
        }
        for (category in SoundCategory.ALL) {
            val entries = library.byCategory(category)
            listContainer.addView(
                TextView(this).apply {
                    text = "$category (${entries.size})"
                    setTextColor(Color.rgb(120, 140, 160))
                    typeface = Typeface.MONOSPACE
                    textSize = 14f
                }
            )
            if (entries.isEmpty()) {
                listContainer.addView(
                    TextView(this).apply {
                        text = "  (empty)"
                        setTextColor(Color.rgb(80, 80, 85))
                    }
                )
            } else {
                for (entry in entries) listContainer.addView(entryRow(entry))
            }
        }
    }

    private fun entryRow(entry: SampleEntry): TextView = TextView(this).apply {
        text = if (entry.tags.isBlank()) "  ${entry.displayName}" else "  ${entry.displayName}  [${entry.tags}]"
        setTextColor(Color.WHITE)
        setPadding(8, 8, 8, 8)
        setOnClickListener { preview(entry) }
        setOnLongClickListener {
            AlertDialog.Builder(this@SoundLibraryActivity)
                .setTitle(entry.displayName)
                .setItems(arrayOf("Move to Category", "Mixer", "Tags")) { _, which ->
                    when (which) {
                        0 -> showRecategorizeDialog(entry)
                        1 -> EffectsMenu.show(this@SoundLibraryActivity, libraryEffectsTarget(entry))
                        2 -> editTags(entry)
                    }
                }
                .show()
            true
        }
    }

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
            SliceExporter.replaceLibraryEntry(this, entry, processed)
            refresh()
        },
    )

    private fun preview(entry: SampleEntry) {
        try {
            AudioPlayback.playOneShot(SampleLoader.decode(contentResolver, entry.uri), context = this)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't play ${entry.displayName}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun editTags(entry: SampleEntry) {
        val input = EditText(this).apply {
            setText(entry.tags)
            hint = "kick, dry, 808"
        }
        AlertDialog.Builder(this)
            .setTitle("Tags")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                library.setTags(entry, input.text.toString())
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRecategorizeDialog(entry: SampleEntry) {
        val categories = SoundCategory.ALL.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Move \"${entry.displayName}\" to")
            .setItems(categories) { _, which ->
                library.setCategory(entry, categories[which])
                refresh()
            }
            .show()
    }

    private fun promptCategoryThenImport(uris: List<Uri>) {
        val categories = SoundCategory.ALL.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Category for ${uris.size} sound(s)")
            .setItems(categories) { _, which -> importAs(uris, categories[which]) }
            .show()
    }

    private fun importAs(uris: List<Uri>, category: String) {
        val entries = uris.map { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Grant couldn't be persisted; the sample still works this session.
            }
            SampleEntry(uri, SampleLoader.queryDisplayName(contentResolver, uri), category)
        }
        library.add(entries)
        refresh()
    }
}
