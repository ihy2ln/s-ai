package com.sai.core.project

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectPlaybackMathTest {

    @Test
    fun `pitch offset shifts playback rate`() {
        val rate = 2.0.pow((60 + 12 - 60) / 12.0).toFloat()
        assertEquals(2.0f, rate)
    }

    @Test
    fun `master volume scales step volume`() {
        val scaled = (100 * 64 / 127.0).toInt().coerceIn(0, 127)
        assertEquals(50, scaled)
    }
}
