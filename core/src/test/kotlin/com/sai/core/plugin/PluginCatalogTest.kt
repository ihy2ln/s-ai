package com.sai.core.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginCatalogTest {

    @Test
    fun `built-in home modules stay in the catalog`() {
        val homes = PluginCatalog.homeModules()
        assertEquals(
            listOf("SAMPLER", "SYNTH", "PADS", "TRACKER", "STEP_SEQUENCER"),
            homes.map { it.homeModule },
        )
        assertTrue(homes.all { it.format == PluginFormat.BUILTIN })
        assertTrue(homes.all { it.canAddToHome })
        assertTrue(homes.none { it.canInsertOnMixer })
    }

    @Test
    fun `catalog includes vst2 and vst3 instruments and effects`() {
        val instruments = PluginCatalog.instruments()
        val effects = PluginCatalog.effects()
        assertTrue(instruments.any { it.format == PluginFormat.VST2 })
        assertTrue(instruments.any { it.format == PluginFormat.VST3 })
        assertTrue(effects.any { it.format == PluginFormat.VST2 })
        assertTrue(effects.any { it.format == PluginFormat.VST3 })
        assertTrue(instruments.all { it.canAddToHome })
        assertTrue(effects.all { it.canInsertOnMixer })
        assertTrue(effects.any { it.canAddToHome })
    }

    @Test
    fun `search matches name format and category`() {
        val delay = PluginCatalog.search(PluginQuery(text = "delay"))
        assertEquals(listOf("vst3.delay"), delay.map { it.id })
        val vst2 = PluginCatalog.search(PluginQuery(format = PluginFormat.VST2))
        assertTrue(vst2.isNotEmpty())
        assertTrue(vst2.all { it.format == PluginFormat.VST2 })
        val keys = PluginCatalog.search(PluginQuery(category = PluginCategory.KEYS))
        assertTrue(keys.any { it.id == "vst3.pulse-keys" })
    }

    @Test
    fun `role filter hides other kinds`() {
        val onlyFx = PluginCatalog.search(PluginQuery(role = PluginRole.EFFECT))
        assertTrue(onlyFx.all { it.role == PluginRole.EFFECT })
        assertTrue(onlyFx.none { it.homeModule == "SAMPLER" })
    }

    @Test
    fun `enabled ids hide disabled vst plugins but keep home modules`() {
        val enabled = setOf("vst3.delay", "vst2.saw-lead")
        val filtered = PluginCatalog.search(PluginQuery(enabledIds = enabled))
        assertTrue(filtered.any { it.id == "home.sampler" })
        assertTrue(filtered.any { it.id == "vst3.delay" })
        assertTrue(filtered.none { it.id == "vst3.chorus" })
    }

    @Test
    fun `every catalog id is unique and lookup works`() {
        val ids = PluginCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in ids) {
            assertNotNull(PluginCatalog.byId(id))
        }
    }

    @Test
    fun `toggleable list excludes built-in home modules`() {
        assertTrue(PluginCatalog.toggleable().none { it.role == PluginRole.HOME })
        assertTrue(PluginCatalog.toggleable().isNotEmpty())
    }
}
