package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class SwingTest {

    @Test
    fun `zero swing keeps even spacing`() {
        assertEquals(0.0, Swing.oddDelayFraction(0))
        assertEquals(1.0, Swing.intervalFraction(0, 0))
        assertEquals(1.0, Swing.intervalFraction(1, 0))
        assertEquals(8, Swing.startFrame(2, 4, 0))
    }

    @Test
    fun `full swing delays odd sixteenths by half a step`() {
        assertEquals(0.5, Swing.oddDelayFraction(100))
        assertEquals(1.5, Swing.intervalFraction(0, 100))
        assertEquals(0.5, Swing.intervalFraction(1, 100))
        assertEquals(6, Swing.startFrame(1, 4, 100))
        assertEquals(8, Swing.startFrame(2, 4, 100))
    }

    @Test
    fun `fifty percent is a quarter-step delay`() {
        assertEquals(0.25, Swing.oddDelayFraction(50))
        assertEquals(1.25, Swing.intervalFraction(0, 50))
        assertEquals(0.75, Swing.intervalFraction(1, 50))
    }
}
