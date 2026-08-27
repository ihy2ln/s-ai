package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.sai.core.audio.InsertChain
import com.sai.core.audio.InsertSlot
import com.sai.core.plugin.ChainPresets
import com.sai.core.plugin.OneKnobs
import com.sai.core.plugin.PluginInsert
import com.sai.core.plugin.PluginRole

/** Ordered mixer insert chain: add, bypass, reorder, presets, Delay-before-Reverb / Tune-before-EQ hints. */
object InsertChainMenu {

    fun show(
        context: Context,
        title: String,
        current: InsertChain,
        onSave: (InsertChain) -> Unit,
    ) {
        var chain = current
        val density = context.resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val hint = TextView(context).apply {
            setTextColor(Color.rgb(140, 150, 160))
            textSize = 12f
            setPadding(0, 0, 0, pad / 2)
        }
        val guide = TextView(context).apply {
            text = "Order matters. Delay before Reverb. Tune before EQ. Mix defaults stay conservative."
            setTextColor(Color.rgb(140, 150, 160))
            textSize = 11f
            setPadding(0, 0, 0, pad / 2)
        }

        lateinit var refresh: () -> Unit

        fun persist(next: InsertChain) {
            chain = next
            onSave(chain)
            refresh()
        }

        fun editSlot(index: Int, slot: InsertSlot) {
            InsertFxMenu.showKnobsForKind(
                context,
                "$title · ${PluginInsert.displayName(slot)}",
                slot.kind,
                slot,
            ) { edited ->
                persist(chain.withSlot(index, edited.copy(engineId = edited.engineId.ifBlank { slot.engineId })))
            }
        }

        fun appendPlugin() {
            ModuleBrowser.show(
                context = context,
                title = "Add to chain",
                initialRole = PluginRole.EFFECT,
            ) { plugin ->
                if (plugin == null) return@show
                if (OneKnobs.byId(plugin.id) != null) {
                    InsertFxMenu.showAmount(context, plugin.name) { amount ->
                        val slot = OneKnobs.slot(plugin.id, amount) ?: return@showAmount
                        persist(chain.plus(slot))
                    }
                    return@show
                }
                val draft = PluginInsert.slotFor(plugin) ?: return@show
                InsertFxMenu.showKnobsForKind(context, plugin.name, draft.kind, draft) { slot ->
                    persist(
                        chain.plus(
                            slot.copy(engineId = slot.engineId.ifBlank { plugin.engineId.ifBlank { plugin.id } }),
                        ),
                    )
                }
            }
        }

        fun applyPreset() {
            val names = ChainPresets.all.map { "${it.name} — ${it.purpose}" }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Chain preset")
                .setItems(names) { _, which ->
                    persist(ChainPresets.all[which].chain())
                    Toast.makeText(context, "Applied ${ChainPresets.all[which].name}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        refresh = {
            val order = chain.orderHint()
            hint.text = order ?: "Chain: ${
                chain.slots.joinToString(" → ") { PluginInsert.displayName(it) }.ifBlank { "empty" }
            }"
            hint.setTextColor(if (order != null) Color.rgb(220, 160, 70) else Color.rgb(140, 150, 160))
            list.removeAllViews()
            if (chain.slots.isEmpty()) {
                list.addView(
                    TextView(context).apply {
                        text = "Empty chain. Add an effect or apply a starter preset."
                        setTextColor(Color.rgb(140, 150, 160))
                        textSize = 13f
                        setPadding(0, pad, 0, pad)
                    },
                )
            }
            for ((index, slot) in chain.slots.withIndex()) {
                list.addView(
                    slotRow(
                        context = context,
                        index = index,
                        slot = slot,
                        lastIndex = chain.slots.lastIndex,
                        onBypass = {
                            persist(chain.withSlot(index, slot.copy(bypassed = !slot.bypassed)))
                        },
                        onMove = { delta -> persist(chain.moved(index, delta)) },
                        onEdit = { editSlot(index, slot) },
                        onRemove = { persist(chain.without(index)) },
                    ),
                )
            }
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply {
                text = "Add"
                setOnClickListener { appendPlugin() }
            })
            addView(Button(context).apply {
                text = "Presets"
                setOnClickListener { applyPreset() }
            })
            addView(Button(context).apply {
                text = "Clear"
                setOnClickListener { persist(InsertChain()) }
            })
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(guide)
            addView(hint)
            addView(
                ScrollView(context).apply { addView(list) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (220 * density).toInt()),
            )
            addView(actions)
        }

        refresh()
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(content)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun slotRow(
        context: Context,
        index: Int,
        slot: InsertSlot,
        lastIndex: Int,
        onBypass: () -> Unit,
        onMove: (Int) -> Unit,
        onEdit: () -> Unit,
        onRemove: () -> Unit,
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val name = TextView(context).apply {
            text = "${index + 1}. ${slot.shortLabel()} ${PluginInsert.displayName(slot)}"
            setTextColor(if (slot.isActive) Color.WHITE else Color.rgb(120, 125, 130))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun tiny(label: String, enabled: Boolean = true, click: () -> Unit) = Button(context).apply {
            text = label
            textSize = 11f
            isEnabled = enabled
            minHeight = 0
            minimumHeight = 0
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setOnClickListener { click() }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            addView(name)
            addView(tiny(if (slot.bypassed) "BYP" else "IN", click = onBypass))
            addView(tiny("↑", index > 0) { onMove(-1) })
            addView(tiny("↓", index < lastIndex) { onMove(1) })
            addView(tiny("Edit", click = onEdit))
            addView(tiny("×", click = onRemove))
        }
    }
}
