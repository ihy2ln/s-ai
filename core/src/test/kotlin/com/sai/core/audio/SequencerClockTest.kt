package com.sai.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SequencerClockTest {

    @Test
    fun `deadline is start plus elapsed milliseconds in nanos`() {
        val start = 1_000_000_000L
        assertEquals(1_000_000_000L, SequencerClock.deadlineNanos(start, 0.0))
        assertEquals(1_020_000_000L, SequencerClock.deadlineNanos(start, 20.0))
        assertEquals(1_125_000_000L, SequencerClock.deadlineNanos(start, 125.0))
    }

    @Test
    fun `waitUntil returns immediately when the deadline has already passed`() {
        val start = System.nanoTime()
        SequencerClock.waitUntil(start - 1_000_000L) { true }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue(elapsedMs < 50.0, "past deadline should not sleep, was ${elapsedMs}ms")
    }

    @Test
    fun `waitUntil stops when running becomes false`() {
        val start = System.nanoTime()
        SequencerClock.waitUntil(start + 5_000_000_000L) { false }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue(elapsedMs < 50.0, "stopped clock should not wait, was ${elapsedMs}ms")
    }
}
