package com.sai.core.layout

/** Pure height math for home-screen module divider drags. */
object ModuleResize {

    fun drag(
        heights: MutableList<Float>,
        handleIndex: Int,
        deltaDp: Float,
        minDp: Float,
        maxDp: Float,
    ): Boolean {
        if (handleIndex !in heights.indices || deltaDp == 0f) return false
        val belowIndex = handleIndex + 1

        if (deltaDp > 0f) {
            val grow = minOf(deltaDp, maxDp - heights[handleIndex])
            if (grow <= 0f) return false
            heights[handleIndex] += grow
            if (belowIndex in heights.indices && heights[belowIndex] > minDp) {
                val shrink = minOf(grow, heights[belowIndex] - minDp)
                heights[belowIndex] -= shrink
            }
            return true
        }

        val shrink = minOf(-deltaDp, heights[handleIndex] - minDp)
        if (shrink <= 0f) return false
        heights[handleIndex] -= shrink
        if (belowIndex in heights.indices) {
            heights[belowIndex] = (heights[belowIndex] + shrink).coerceAtMost(maxDp)
        }
        return true
    }
}
