package com.sai.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleResizeTest {

    @Test
    fun `dragging a divider down expands only the module above it`() {
        val heights = mutableListOf(100f, 100f, 100f)
        assertTrue(ModuleResize.drag(heights, handleIndex = 0, deltaDp = 40f, minDp = 64f, maxDp = 1200f))
        assertEquals(140f, heights[0])
        assertEquals(100f, heights[1])
        assertEquals(100f, heights[2])
    }

    @Test
    fun `dragging a divider up shrinks only the module above it`() {
        val heights = mutableListOf(140f, 80f)
        assertTrue(ModuleResize.drag(heights, handleIndex = 0, deltaDp = -30f, minDp = 64f, maxDp = 1200f))
        assertEquals(110f, heights[0])
        assertEquals(80f, heights[1])
    }

    @Test
    fun `bottom divider expands only the last module`() {
        val heights = mutableListOf(100f, 80f)
        assertTrue(ModuleResize.drag(heights, handleIndex = 1, deltaDp = 50f, minDp = 64f, maxDp = 1200f))
        assertEquals(100f, heights[0])
        assertEquals(130f, heights[1])
    }
}
