package com.sai.app

import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Sizes home-screen modules to fill the viewport in landscape. */
object ModuleLayoutFit {

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
            restoreStoredHeights(column, entries, density)
            return
        }

        val scrollHeight = scroll.height
        if (scrollHeight <= 0) return

        val handleHeightPx = (28 * density).toInt()
        val handleCount = (entries.size - 1).coerceAtLeast(0)
        val available = scrollHeight - handleCount * handleHeightPx
        if (available <= 0) return

        val totalWeight = entries.sumOf { it.heightDp.toDouble() }
        if (totalWeight <= 0.0) return

        val minPx = (minHeightDp * density).toInt()
        for (index in entries.indices) {
            val wrapper = column.getChildAt(index * 2) ?: continue
            val share = entries[index].heightDp / totalWeight
            wrapper.layoutParams = wrapper.layoutParams.apply {
                height = (available * share).toInt().coerceAtLeast(minPx)
            }
        }
        column.requestLayout()
    }

    private fun restoreStoredHeights(column: LinearLayout, entries: List<ModuleEntry>, density: Float) {
        for (index in entries.indices) {
            val wrapper = column.getChildAt(index * 2) ?: continue
            wrapper.layoutParams = wrapper.layoutParams.apply {
                height = (entries[index].heightDp * density).toInt()
            }
        }
        column.requestLayout()
    }
}
