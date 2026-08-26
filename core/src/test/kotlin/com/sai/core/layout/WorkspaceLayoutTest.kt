package com.sai.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceLayoutTest {

    @Test
    fun `focus mode gives leftover height to the active module`() {
        val heights = WorkspaceLayoutMath.focusHeights(count = 4, focusedIndex = 1, availableDp = 400f, collapsedDp = 40f)
        assertEquals(listOf(40f, 280f, 40f, 40f), heights)
        assertEquals(400f, heights.sum())
    }

    @Test
    fun `focus index is clamped`() {
        val heights = WorkspaceLayoutMath.focusHeights(count = 2, focusedIndex = 9, availableDp = 200f, collapsedDp = 40f)
        assertEquals(40f, heights[0])
        assertEquals(160f, heights[1])
    }

    @Test
    fun `default grid tiles two columns`() {
        val boxes = WorkspaceLayoutMath.defaultGrid(count = 4, canvasW = 800f, canvasH = 400f, gap = 10f)
        assertEquals(4, boxes.size)
        assertEquals(boxes[0].wDp, boxes[1].wDp, 0.01f)
        assertTrue(boxes[1].xDp > boxes[0].xDp)
        assertTrue(boxes[2].yDp > boxes[0].yDp)
    }

    @Test
    fun `clamp keeps a box on the canvas`() {
        val clamped = WorkspaceLayoutMath.clampBox(
            ModuleBoxFrame(xDp = -40f, yDp = 900f, wDp = 50f, hDp = 2000f),
            canvasW = 400f,
            canvasH = 300f,
            minDp = 120f,
        )
        assertEquals(0f, clamped.xDp)
        assertTrue(clamped.yDp >= 0f)
        assertTrue(clamped.wDp >= 120f)
        assertTrue(clamped.yDp + clamped.hDp <= 300.01f)
    }

    @Test
    fun `select grow enlarges a box`() {
        val grown = WorkspaceLayoutMath.selectGrow(
            ModuleBoxFrame(10f, 10f, 100f, 100f),
            canvasW = 400f,
            canvasH = 300f,
            factor = 1.5f,
            minDp = 80f,
        )
        assertTrue(grown.wDp > 100f)
        assertTrue(grown.hDp > 100f)
        assertTrue(grown.xDp + grown.wDp <= 400.01f)
    }

    @Test
    fun `fromName falls back to stack`() {
        assertEquals(WorkspaceLayout.FOCUS, WorkspaceLayout.fromName("focus"))
        assertEquals(WorkspaceLayout.STACK, WorkspaceLayout.fromName("nope"))
        assertEquals(WorkspaceLayout.BOXES, WorkspaceLayout.fromName("BOXES"))
    }
}
