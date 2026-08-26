package com.sai.core.project

/** Stable integer IDs for library instruments, independent of display sort order. */
object StableIds {
    const val UNASSIGNED = -1

    /**
     * Assign unique IDs, preserving any already-set values. Null / [UNASSIGNED] slots
     * receive the next unused integer. Passing a previously index-based list (all
     * unassigned, in the old display order) yields 0, 1, 2… so existing phrase data
     * keeps pointing at the same sounds.
     */
    fun assign(existing: List<Int?>, nextIdHint: Int = 0): Assignment {
        val used = existing.mapNotNull { id -> id?.takeIf { it >= 0 } }.toMutableSet()
        var next = maxOf(nextIdHint, (used.maxOrNull() ?: -1) + 1, 0)
        val ids = existing.map { raw ->
            val keep = raw?.takeIf { it >= 0 }
            if (keep != null) {
                keep
            } else {
                while (next in used) next++
                val id = next++
                used.add(id)
                id
            }
        }
        val nextId = maxOf(next, (used.maxOrNull() ?: -1) + 1)
        return Assignment(ids, nextId)
    }

    data class Assignment(val ids: List<Int>, val nextId: Int)
}
