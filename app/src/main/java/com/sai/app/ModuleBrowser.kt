package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sai.core.plugin.PluginCatalog
import com.sai.core.plugin.PluginCategory
import com.sai.core.plugin.PluginDescriptor
import com.sai.core.plugin.PluginFormat
import com.sai.core.plugin.PluginQuery
import com.sai.core.plugin.PluginRole

/**
 * Compact module browser (FL Mobile plugin list + BandLab category picker):
 * search, Instruments/Effects/Home tabs, format chips, visual tiles.
 */
object ModuleBrowser {

    fun show(
        context: Context,
        title: String = "Add Module",
        initialRole: PluginRole? = null,
        includeOff: Boolean = false,
        onPick: (PluginDescriptor?) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val settings = PluginSettings(context)
        var role = initialRole
        var format: PluginFormat? = null
        var category: PluginCategory? = null
        var queryText = ""

        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val emptyLabel = TextView(context).apply {
            setTextColor(Color.rgb(140, 150, 160))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (24 * density).toInt(), 0, (24 * density).toInt())
        }

        val search = EditText(context).apply {
            hint = "Search modules"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(100, 110, 120))
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundColor(Color.rgb(28, 30, 34))
            setPadding(pad, (8 * density).toInt(), pad, (8 * density).toInt())
        }

        val roleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val formatRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val categoryRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun enabledIds(): Set<String> =
            PluginCatalog.toggleable().filter { settings.isEnabled(it.id) }.map { it.id }.toSet()

        lateinit var refresh: () -> Unit

        fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
            val accent = AppTheme.accentColor(context)
            return TextView(context).apply {
                text = label
                textSize = 12f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                gravity = Gravity.CENTER
                setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                if (selected) {
                    setBackgroundColor(accent)
                } else {
                    background = ContextCompat.getDrawable(context, R.drawable.plugin_chip_bg)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = (6 * density).toInt() }
                setOnClickListener { onClick() }
            }
        }

        fun rebuildChips() {
            roleRow.removeAllViews()
            val roles = listOf<Pair<String, PluginRole?>>(
                "All" to null,
                "Instruments" to PluginRole.INSTRUMENT,
                "Effects" to PluginRole.EFFECT,
                "Home" to PluginRole.HOME,
            )
            for ((label, value) in roles) {
                roleRow.addView(chip(label, role == value) {
                    role = value
                    category = null
                    refresh()
                })
            }
            formatRow.removeAllViews()
            val formats = listOf<Pair<String, PluginFormat?>>(
                "Any format" to null,
                "Built-in" to PluginFormat.BUILTIN,
                "VST2" to PluginFormat.VST2,
                "VST3" to PluginFormat.VST3,
            )
            for ((label, value) in formats) {
                formatRow.addView(chip(label, format == value) {
                    format = value
                    refresh()
                })
            }
            categoryRow.removeAllViews()
            categoryRow.addView(chip("All categories", category == null) {
                category = null
                refresh()
            })
            for (item in PluginCatalog.categoriesFor(role)) {
                categoryRow.addView(chip(item.label, category == item) {
                    category = item
                    refresh()
                })
            }
        }

        fun tile(plugin: PluginDescriptor, enabled: Boolean): LinearLayout {
            val colorBar = TextView(context).apply {
                text = plugin.format.badge
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
                setBackgroundColor(plugin.tileColor)
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            val name = TextView(context).apply {
                text = plugin.name
                setTextColor(if (enabled) Color.WHITE else Color.rgb(90, 95, 105))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), 0)
            }
            val meta = TextView(context).apply {
                text = "${plugin.roleLabel()} · ${plugin.category.label}"
                setTextColor(Color.rgb(130, 140, 150))
                textSize = 10f
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), (8 * density).toInt())
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.plugin_tile_bg)
                isEnabled = true
                alpha = if (enabled) 1f else 0.45f
                addView(colorBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(name)
                addView(meta)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                }
            }
        }

        fun offTile(): LinearLayout {
            val colorBar = TextView(context).apply {
                text = "OFF"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.rgb(70, 74, 82))
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            val name = TextView(context).apply {
                text = "Off"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), 0)
            }
            val meta = TextView(context).apply {
                text = "Clear this insert"
                setTextColor(Color.rgb(130, 140, 150))
                textSize = 10f
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), (8 * density).toInt())
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.plugin_tile_bg)
                addView(colorBar)
                addView(name)
                addView(meta)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                }
            }
        }

        var dialog: AlertDialog? = null

        refresh = {
            rebuildChips()
            grid.removeAllViews()
            val matches = PluginCatalog.search(
                PluginQuery(
                    text = queryText,
                    role = role,
                    format = format,
                    category = category,
                ),
            )
            val enabled = enabledIds()
            if (matches.isEmpty() && !includeOff) {
                emptyLabel.text = if (queryText.isBlank()) {
                    "No modules in this category."
                } else {
                    "No modules match \"$queryText\"."
                }
                grid.addView(emptyLabel)
            } else {
                val tiles = mutableListOf<Pair<LinearLayout, () -> Unit>>()
                if (includeOff) {
                    tiles.add(offTile() to {
                        dialog?.dismiss()
                        onPick(null)
                    })
                }
                for (plugin in matches) {
                    val isOn = plugin.role == PluginRole.HOME || plugin.id in enabled
                    val view = tile(plugin, isOn)
                    tiles.add(view to {
                        if (!isOn) {
                            android.widget.Toast.makeText(
                                context,
                                "Enable ${plugin.name} in Menu > Plugins first",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            dialog?.dismiss()
                            onPick(plugin)
                        }
                    })
                }
                val columns = 3
                var row: LinearLayout? = null
                for ((index, item) in tiles.withIndex()) {
                    if (index % columns == 0) {
                        row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                        grid.addView(row)
                    }
                    val (view, click) = item
                    view.setOnClickListener { click() }
                    row?.addView(view)
                }
                val leftover = tiles.size % columns
                if (leftover != 0) {
                    repeat(columns - leftover) {
                        row?.addView(android.view.View(context), LinearLayout.LayoutParams(0, 1, 1f))
                    }
                }
            }
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                queryText = s?.toString().orEmpty()
                refresh()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        val hint = TextView(context).apply {
            text = "Built-in modules stay available. VST2/VST3 entries are in-app instruments and effects."
            setTextColor(Color.rgb(120, 130, 140))
            textSize = 11f
            setPadding(0, 0, 0, (6 * density).toInt())
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 20))
            setPadding(pad, pad, pad, pad)
            addView(hint)
            addView(search)
            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(roleRow)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (8 * density).toInt()
                },
            )
            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(formatRow)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (6 * density).toInt()
                },
            )
            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(categoryRow)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (6 * density).toInt()
                    bottomMargin = (8 * density).toInt()
                },
            )
            addView(
                ScrollView(context).apply { addView(grid) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (260 * density).toInt()),
            )
        }

        refresh()
        dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(content)
            .setNegativeButton("Close", null)
            .show()
    }
}
