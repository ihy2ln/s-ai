package com.sai.core.audio

/** Mixer insert routing: Channel Rack `mixerTrack` 1–[STRIP_COUNT] maps onto these strips;
 *  0 means unassigned (summed to the master bus only). */
object MixerMath {

    const val STRIP_COUNT = 8

    data class Strip(
        val muted: Boolean = false,
        val soloed: Boolean = false,
        val volume: Float = 1f,
    )

    data class Channel(
        val muted: Boolean = false,
        val soloed: Boolean = false,
        val volume: Float = 1f,
        val pan: Float = 0.5f,
        val mixerTrack: Int = 0,
    )

    fun stripIndex(mixerTrack: Int): Int? {
        val index = mixerTrack - 1
        return if (index in 0 until STRIP_COUNT) index else null
    }

    fun anySolo(strips: List<Strip>): Boolean = strips.any { it.soloed }

    fun anyRackSolo(channels: List<Channel>): Boolean = channels.any { it.soloed }

    fun isAudible(
        channel: Channel,
        strips: List<Strip>,
        masterMuted: Boolean,
        anyRackSolo: Boolean = false,
    ): Boolean {
        if (masterMuted || channel.muted) return false
        if (anyRackSolo && !channel.soloed) return false
        val index = stripIndex(channel.mixerTrack)
        val soloing = anySolo(strips)
        if (index == null) return !soloing
        val strip = strips.getOrElse(index) { Strip() }
        if (strip.muted) return false
        if (soloing && !strip.soloed) return false
        return true
    }

    /** Linear amplitude: step 0–127, all other faders 0–1. */
    fun linearGain(
        stepVolume: Int,
        rackVolume: Float,
        stripVolume: Float,
        mixerMaster: Float,
        projectMaster: Float,
    ): Float {
        val step = stepVolume.coerceIn(0, 127) / 127f
        return (step *
            rackVolume.coerceIn(0f, 1f) *
            stripVolume.coerceIn(0f, 1f) *
            mixerMaster.coerceIn(0f, 1f) *
            projectMaster.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)
    }

    fun gainDb(linear: Float): Double =
        if (linear <= 0f) -80.0 else 20.0 * kotlin.math.log10(linear.toDouble())

    fun stripVolume(channel: Channel, strips: List<Strip>): Float {
        val index = stripIndex(channel.mixerTrack) ?: return 1f
        return strips.getOrElse(index) { Strip() }.volume
    }
}
