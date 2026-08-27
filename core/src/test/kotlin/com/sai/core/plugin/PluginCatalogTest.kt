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
        assertTrue(delay.any { it.id == "vst3.delay" })
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
    fun `job filter keeps vocal tools`() {
        val vocals = PluginCatalog.search(PluginQuery(job = PluginJob.VOCALS))
        assertTrue(vocals.any { it.id == "vst2.deess" })
        assertTrue(vocals.any { it.id == "vst3.tune" })
        assertTrue(vocals.none { it.role == PluginRole.HOME })
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

    @Test
    fun `wave1 plugins remain and bandlab starters alias or sit beside them`() {
        assertNotNull(PluginCatalog.byId("vst3.pulse-keys"))
        assertNotNull(PluginCatalog.byId("vst2.saw-lead"))
        assertNotNull(PluginCatalog.byId("vst3.sub-bass"))
        assertNotNull(PluginCatalog.byId("vst2.bassline"))
        assertNotNull(PluginCatalog.byId("vst3.supersaw"))
        val keys = PluginCatalog.byId("vst3.keys")
        assertEquals("home.synth", keys?.aliasOf)
        assertEquals("SYNTH", keys?.homeModule)
        val drive = PluginCatalog.byId("vst2.drive")
        assertEquals("DISTORTION", drive?.insertKind)
        assertEquals("vst2.distort", drive?.aliasOf)
        assertNotNull(PluginCatalog.byId("knob.brighter"))
        assertNotNull(PluginCatalog.byId("vst3.phaser") ?: PluginCatalog.byId("vst2.phaser"))
    }

    @Test
    fun `json export includes jobs engine ids and chain presets`() {
        val json = PluginCatalog.exportJson()
        assertTrue(json.contains("\"displayName\""))
        assertTrue(json.contains("\"engineId\""))
        assertTrue(json.contains("chain.lead-vocal"))
        assertTrue(json.contains("knob.echo"))
        assertTrue(json.contains("VOCALS"))
    }

    @Test
    fun `plugin insert maps slapback and one-knobs onto engines`() {
        val slap = PluginInsert.slotFor(PluginCatalog.byId("vst3.slapback")!!)
        assertEquals(com.sai.core.audio.InsertKind.DELAY, slap?.kind)
        assertTrue((slap?.params?.get("time") ?: 999.0) < 120.0)
        val brighter = PluginInsert.slotFor(PluginCatalog.byId("knob.brighter")!!, amount = 1.0)
        assertEquals(com.sai.core.audio.InsertKind.EQUALIZER, brighter?.kind)
        assertTrue((brighter?.params?.get("b7") ?: 0.0) > 0.0)
    }
}
