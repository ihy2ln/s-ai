package com.sai.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
        val pad = (12 * density).toInt()

        val title = Ui.screenTitle(this, "SOUNDS")
        val addButton = Ui.compactButton(this, "Add Sounds") { importLauncher.launch(arrayOf("audio/*")) }

        searchInput = Ui.input(this, hint = "Search name, category, tags").apply {
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
            setBackgroundColor(AppTheme.canvas)
            addView(Ui.headerBar(this@SoundLibraryActivity) {
                addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(addButton)
                addView(PillButton.create(this@SoundLibraryActivity, "N") { NavMenu.show(this@SoundLibraryActivity) })
            })
            addView(searchInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * density).toInt()
                topMargin = (4 * density).toInt()
            })
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
            listContainer.addView(Ui.sectionLabel(this, "Results (${filtered.size})"))
            if (filtered.isEmpty()) {
                listContainer.addView(Ui.emptyState(this, "No matches"))
            } else {
                for (entry in filtered) listContainer.addView(entryRow(entry))
            }
            return
        }
        for (category in SoundCategory.ALL) {
            val entries = library.byCategory(category)
            listContainer.addView(Ui.sectionLabel(this, "$category (${entries.size})"))
            if (entries.isEmpty()) {
                listContainer.addView(Ui.emptyState(this, "Empty"))
            } else {
                for (entry in entries) listContainer.addView(entryRow(entry))
            }
        }
    }

    private fun entryRow(entry: SampleEntry): LinearLayout = Ui.listRow(
        context = this,
        title = entry.displayName,
        subtitle = if (entry.tags.isBlank()) entry.category else "${entry.category} · ${entry.tags}",
        leading = AppTheme.accentColor(this),
        trailing = "▶",
        onClick = { preview(entry) },
        onLongClick = {
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
        },
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
        val input = Ui.input(this, hint = "kick, dry, 808", text = entry.tags)
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
