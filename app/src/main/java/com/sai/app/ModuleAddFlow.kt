package com.sai.app

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import com.sai.core.audio.InstrumentVoice
import com.sai.core.plugin.OneKnobs
import com.sai.core.plugin.PluginDescriptor
import com.sai.core.plugin.PluginInsert
import com.sai.core.plugin.PluginRole

/** Shared add/replace flow for the module browser: Home stack, Channel Rack, mixer insert chain. */
object ModuleAddFlow {

    fun showBrowser(
        context: Context,
        title: String = "Add Module",
        initialRole: PluginRole? = null,
        onHomeChanged: (() -> Unit)? = null,
        onRackChanged: (() -> Unit)? = null,
    ) {
        ModuleBrowser.show(context, title, initialRole = initialRole) { plugin ->
            if (plugin != null) handlePick(context, plugin, onHomeChanged, onRackChanged)
        }
    }

    fun handlePick(
        context: Context,
        plugin: PluginDescriptor,
        onHomeChanged: (() -> Unit)? = null,
        onRackChanged: (() -> Unit)? = null,
    ) {
        when (plugin.role) {
            PluginRole.HOME -> addHome(context, plugin, onHomeChanged)
            PluginRole.INSTRUMENT -> {
                val hasVoice = InstrumentVoice.kindForHomeModule(plugin.homeModule ?: "") != null && plugin.aliasOf == null
                if (!hasVoice) {
                    addHome(context, plugin, onHomeChanged)
                    return
                }
                AlertDialog.Builder(context)
                    .setTitle(plugin.name)
                    .setItems(arrayOf("Add to Home", "Add to Channel Rack")) { _, which ->
                        if (which == 0) addHome(context, plugin, onHomeChanged)
                        else addInstrumentToRack(context, plugin, onRackChanged)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            PluginRole.EFFECT -> {
                val options = mutableListOf<String>()
                if (plugin.canAddToHome) options.add("Add to Home")
                if (plugin.canInsertOnMixer) options.add("Insert on Mixer")
                if (options.isEmpty()) return
                AlertDialog.Builder(context)
                    .setTitle(plugin.name)
                    .setItems(options.toTypedArray()) { _, which ->
                        if (options[which] == "Add to Home") addHome(context, plugin, onHomeChanged)
                        else insertEffectOnMixer(context, plugin)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    fun addHome(context: Context, plugin: PluginDescriptor, onHomeChanged: (() -> Unit)? = null) {
        val type = PluginModules.moduleType(plugin) ?: return
        val entries = ModuleLayoutStore.load(context)
        if (entries.any { it.type == type }) {
            Toast.makeText(context, "${type.label} is already on screen", Toast.LENGTH_SHORT).show()
            ModuleLayoutStore.setFocusedType(context, type)
            onHomeChanged?.invoke()
            return
        }
        entries.add(ModuleEntry(type, ModuleLayoutStore.defaultHeight(type)))
        ModuleLayoutStore.save(context, entries)
        Toast.makeText(context, "Added ${type.label}", Toast.LENGTH_SHORT).show()
        onHomeChanged?.invoke()
    }

    fun addInstrumentToRack(context: Context, plugin: PluginDescriptor, onRackChanged: (() -> Unit)? = null) {
        val kind = InstrumentVoice.kindForHomeModule(plugin.homeModule ?: "") ?: return
        val wav = InstrumentVoice.render(kind, 60, PluginParamStore.load(context, plugin.id))
        val saved = SliceExporter.saveToLibrary(context, plugin.name, listOf(wav), SoundCategory.SYNTH)
        val placed = ChannelRackStore.sendToRack(context, saved.map { it.id })
        Toast.makeText(context, "Placed $placed × ${plugin.name} on Channel Rack", Toast.LENGTH_SHORT).show()
        onRackChanged?.invoke()
    }

    fun insertEffectOnMixer(context: Context, plugin: PluginDescriptor) {
        val labels = (1..MixerStore.STRIP_COUNT).map { "Insert $it" } + "Master"
        AlertDialog.Builder(context)
            .setTitle("Insert ${plugin.name}")
            .setItems(labels.toTypedArray()) { _, which ->
                val stripIndex = if (which >= MixerStore.STRIP_COUNT) null else which
                fun append(slot: com.sai.core.audio.InsertSlot) {
                    MixerStore.appendInsert(context, stripIndex, slot)
                    Toast.makeText(context, "${plugin.name} appended", Toast.LENGTH_SHORT).show()
                }
                if (OneKnobs.byId(plugin.id) != null) {
                    InsertFxMenu.showAmount(context, plugin.name) { amount ->
                        val slot = OneKnobs.slot(plugin.id, amount) ?: return@showAmount
                        append(slot)
                    }
                    return@setItems
                }
                val draft = PluginInsert.slotFor(plugin) ?: return@setItems
                InsertFxMenu.showKnobsForKind(context, plugin.name, draft.kind, draft) { slot ->
                    append(slot.copy(engineId = slot.engineId.ifBlank { plugin.engineId.ifBlank { plugin.id } }))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
