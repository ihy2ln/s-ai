package com.sai.core.layout

/** Pure height math for home-screen module divider drags. */
object ModuleResize {

    /** Dragging a divider changes only the module above it, so expansion is free (the stack grows). */
    fun drag(
        heights: MutableList<Float>,
        handleIndex: Int,
        deltaDp: Float,
        minDp: Float,
        maxDp: Float,
    ): Boolean {
        if (handleIndex !in heights.indices || deltaDp == 0f) return false
        val next = (heights[handleIndex] + deltaDp).coerceIn(minDp, maxDp)
        if (next == heights[handleIndex]) return false
        heights[handleIndex] = next
        return true
    }
}
