package com.sai.core.audio

/**
 * Sample-accurate-enough sequencer timing: deadlines are computed from a start timestamp plus
 * accumulated musical time, so sleep jitter does not compound from step to step.
 */
object SequencerClock {

    fun deadlineNanos(startNanos: Long, elapsedMillis: Double): Long =
        startNanos + (elapsedMillis * 1_000_000.0).toLong()

    /**
     * Sleeps until [targetNanos]. Uses [Thread.sleep] for the bulk of the wait, then a short
     * spin so the last couple of milliseconds are tighter than a single sleep.
     */
    fun waitUntil(targetNanos: Long, running: () -> Boolean) {
        while (running()) {
            val remaining = targetNanos - System.nanoTime()
            if (remaining <= 0L) return
            if (remaining > SPIN_NANOS) {
                val sleepNanos = remaining - SPIN_NANOS
                try {
                    Thread.sleep(sleepNanos / 1_000_000L, (sleepNanos % 1_000_000L).toInt())
                } catch (e: InterruptedException) {
                    return
                }
            } else {
                while (running() && System.nanoTime() < targetNanos) {
                    // spin
                }
                return
            }
        }
    }

    private const val SPIN_NANOS = 2_000_000L
}
