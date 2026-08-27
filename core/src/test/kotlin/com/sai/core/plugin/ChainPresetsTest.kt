package com.sai.core.plugin

import com.sai.core.audio.InsertKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainPresetsTest {

    @Test
    fun `five starter chain presets exist`() {
        assertEquals(
            listOf("chain.lead-vocal", "chain.vocal-double", "chain.guitar-crunch", "chain.drum-punch", "chain.master-polish"),
            ChainPresets.all.map { it.id },
        )
        for (preset in ChainPresets.all) {
            assertTrue(preset.slots.isNotEmpty(), preset.id)
            assertTrue(preset.slots.none { it.kind == InsertKind.NONE }, preset.id)
            assertTrue(preset.slots.all { it.engineId.isNotBlank() }, preset.id)
        }
    }

    @Test
    fun `lead vocal orders tune before eq and delay before reverb`() {
        val kinds = ChainPresets.byId("chain.lead-vocal")!!.slots.map { it.kind }
        assertTrue(kinds.indexOf(InsertKind.TUNE) < kinds.indexOf(InsertKind.EQUALIZER))
        assertTrue(kinds.indexOf(InsertKind.DELAY) < kinds.indexOf(InsertKind.REVERB))
        assertEquals(null, ChainPresets.byId("chain.lead-vocal")!!.chain().orderHint())
    }

    @Test
    fun `one-knobs are data over existing engines`() {
        assertEquals(8, OneKnobs.all.size)
        val punchy = OneKnobs.slot("knob.punchy", 1.0)!!
        assertEquals(InsertKind.COMPRESSOR, punchy.kind)
        assertTrue((punchy.params["ratio"] ?: 0.0) > 2.0)
        val room = OneKnobs.slot("knob.room", 0.5)!!
        assertEquals(InsertKind.REVERB, room.kind)
        assertTrue((room.params["mix"] ?: 1.0) < 0.5)
    }
}
