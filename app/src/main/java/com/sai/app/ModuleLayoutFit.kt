package com.sai.app

import android.content.res.Configuration
import android.widget.LinearLayout
import android.widget.ScrollView

/** Sizes home-screen modules to fill the viewport in landscape on first layout. */
object ModuleLayoutFit {

    const val HANDLE_HEIGHT_DP = 36f

    fun redistribute(
        scroll: ScrollView,
        column: LinearLayout,
        entries: List<ModuleEntry>,
        density: Float,
        minHeightDp: Float,
        isFullScreen: Boolean,
        orientation: Int,
    ) {
        if (isFullScreen || entries.isEmpty()) return

        if (orientation != Configuration.ORIENTATION_LANDSCAPE) {
            applyStoredHeights(column, entries, density)
            return
        }

        val scrollHeight = scroll.height
        if (scrollHeight <= 0) return

        val handleHeightPx = (HANDLE_HEIGHT_DP * density).toInt()
        val handleCount = entries.size
        val available = scrollHeight - handleCount * handleHeightPx
        if (available <= 0) return

        val totalWeight = entries.sumOf { it.heightDp.toDouble() }.toFloat()
        if (totalWeight <= 0f) return

        val minPx = minOf(
            (minHeightDp * density).toInt(),
            (available / entries.size).coerceAtLeast(1),
        )
        var assigned = 0
        for (index in entries.indices) {
            val wrapper = column.getChildAt(index * 2) ?: continue
            val share = entries[index].heightDp / totalWeight
            val height = if (index == entries.lastIndex) {
                (available - assigned).coerceAtLeast(minPx)
            } else {
                (available * share).toInt().coerceAtLeast(minPx)
            }
            assigned += height
            wrapper.layoutParams = wrapper.layoutParams.apply { this.height = height }
        }
        column.requestLayout()
    }

    fun applyStoredHeights(column: LinearLayout, entries: List<ModuleEntry>, density: Float) {
        for (index in entries.indices) {
            val wrapper = column.getChildAt(index * 2) ?: continue
            wrapper.layoutParams = wrapper.layoutParams.apply {
                height = (entries[index].heightDp * density).toInt()
            }
        }
        column.requestLayout()
    }
}
