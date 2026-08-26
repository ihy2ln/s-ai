package com.sai.core.layout

enum class WorkspaceLayout {
    STACK,
    FOCUS,
    BOXES,
    ;

    companion object {
        fun fromName(raw: String?): WorkspaceLayout =
            values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STACK
    }
}

data class ModuleBoxFrame(
    var xDp: Float,
    var yDp: Float,
    var wDp: Float,
    var hDp: Float,
)

/** Height and box-frame math for Home workspace modes. */
object WorkspaceLayoutMath {

    const val COLLAPSED_DP = 44f
    const val MIN_BOX_DP = 120f
    const val GAP_DP = 10f

    /**
     * Focus (accordion) mode: every module except [focusedIndex] is [collapsedDp] tall;
     * the focused one takes the remaining viewport.
     */
    fun focusHeights(
        count: Int,
        focusedIndex: Int,
        availableDp: Float,
        collapsedDp: Float = COLLAPSED_DP,
    ): List<Float> {
        if (count <= 0) return emptyList()
        val index = focusedIndex.coerceIn(0, count - 1)
        val collapsedTotal = collapsedDp * (count - 1)
        val focused = (availableDp - collapsedTotal).coerceAtLeast(collapsedDp)
        return List(count) { i -> if (i == index) focused else collapsedDp }
    }

    /** Tile [count] boxes in a 2-column landscape grid inside the canvas. */
    fun defaultGrid(
        count: Int,
        canvasW: Float,
        canvasH: Float,
        gap: Float = GAP_DP,
    ): List<ModuleBoxFrame> {
        if (count <= 0) return emptyList()
        val cols = if (count <= 1) 1 else 2
        val rows = (count + cols - 1) / cols
        val innerW = (canvasW - gap * (cols + 1)).coerceAtLeast(MIN_BOX_DP)
        val innerH = (canvasH - gap * (rows + 1)).coerceAtLeast(MIN_BOX_DP)
        val cellW = innerW / cols
        val cellH = innerH / rows
        return List(count) { i ->
            val col = i % cols
            val row = i / cols
            ModuleBoxFrame(
                xDp = gap + col * (cellW + gap),
                yDp = gap + row * (cellH + gap),
                wDp = cellW,
                hDp = cellH,
            )
        }
    }

    fun clampBox(
        box: ModuleBoxFrame,
        canvasW: Float,
        canvasH: Float,
        minDp: Float = MIN_BOX_DP,
    ): ModuleBoxFrame {
        val w = box.wDp.coerceIn(minDp, canvasW.coerceAtLeast(minDp))
        val h = box.hDp.coerceIn(minDp, canvasH.coerceAtLeast(minDp))
        val x = box.xDp.coerceIn(0f, (canvasW - w).coerceAtLeast(0f))
        val y = box.yDp.coerceIn(0f, (canvasH - h).coerceAtLeast(0f))
        return ModuleBoxFrame(x, y, w, h)
    }

    /** Grow a selected box toward the canvas center, clamped to the canvas. */
    fun selectGrow(
        box: ModuleBoxFrame,
        canvasW: Float,
        canvasH: Float,
        factor: Float = 1.32f,
        minDp: Float = MIN_BOX_DP,
    ): ModuleBoxFrame {
        val w = (box.wDp * factor).coerceAtMost(canvasW)
        val h = (box.hDp * factor).coerceAtMost(canvasH)
        val cx = box.xDp + box.wDp / 2f
        val cy = box.yDp + box.hDp / 2f
        return clampBox(
            ModuleBoxFrame(cx - w / 2f, cy - h / 2f, w, h),
            canvasW,
            canvasH,
            minDp,
        )
    }
}
