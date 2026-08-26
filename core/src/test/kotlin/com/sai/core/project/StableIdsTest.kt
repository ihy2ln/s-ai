package com.sai.core.project

import kotlin.test.Test
import kotlin.test.assertEquals

class StableIdsTest {

    @Test
    fun `unassigned list in display order becomes 0 1 2 so old indexes keep working`() {
        val assignment = StableIds.assign(listOf(null, null, null))
        assertEquals(listOf(0, 1, 2), assignment.ids)
        assertEquals(3, assignment.nextId)
    }

    @Test
    fun `existing ids are preserved when new entries are appended`() {
        val assignment = StableIds.assign(listOf(0, 2, null), nextIdHint = 3)
        assertEquals(listOf(0, 2, 3), assignment.ids)
        assertEquals(4, assignment.nextId)
    }

    @Test
    fun `already assigned ids pass through unchanged`() {
        val assignment = StableIds.assign(listOf(4, 1, 9))
        assertEquals(listOf(4, 1, 9), assignment.ids)
        assertEquals(10, assignment.nextId)
    }
}
