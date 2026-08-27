package com.sai.core.audio

/** Ordered mixer insert chain. Empty or all-bypassed is a no-op. Order is Delay-before-Reverb / Tune-before-EQ. */
data class InsertChain(
    val slots: List<InsertSlot> = emptyList(),
) {
    val isActive: Boolean get() = slots.any { it.isActive }

    fun primary(): InsertSlot = slots.firstOrNull { it.kind != InsertKind.NONE } ?: InsertSlot()

    fun shortLabel(): String {
        val named = slots.filter { it.kind != InsertKind.NONE }
        if (named.isEmpty()) return "fx"
        if (named.size == 1) return named[0].shortLabel()
        return "${named.size}fx"
    }

    fun fingerprint(): String {
        if (!isActive) return "off"
        return slots.joinToString(">") { it.fingerprint() }
    }

    fun plus(slot: InsertSlot): InsertChain {
        if (slot.kind == InsertKind.NONE) return this
        return copy(slots = slots + slot)
    }

    fun withSlot(index: Int, slot: InsertSlot): InsertChain {
        if (index !in slots.indices) return plus(slot)
        val next = slots.toMutableList()
        next[index] = slot
        return copy(slots = next)
    }

    fun without(index: Int): InsertChain {
        if (index !in slots.indices) return this
        return copy(slots = slots.filterIndexed { i, _ -> i != index })
    }

    fun moved(from: Int, delta: Int): InsertChain {
        if (from !in slots.indices) return this
        val to = (from + delta).coerceIn(0, slots.lastIndex)
        if (to == from) return this
        val next = slots.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return copy(slots = next)
    }

    fun orderHint(): String? = ChainHints.forKinds(slots.map { it.kind })

    companion object {
        fun from(slot: InsertSlot): InsertChain =
            if (slot.kind == InsertKind.NONE) InsertChain() else InsertChain(listOf(slot))

        fun of(vararg slots: InsertSlot): InsertChain =
            InsertChain(slots.filter { it.kind != InsertKind.NONE })
    }
}

object ChainHints {
    fun forKinds(kinds: List<InsertKind>): String? {
        val live = kinds.filter { it != InsertKind.NONE }
        val delay = live.indexOfFirst { it == InsertKind.DELAY }
        val reverb = live.indexOfFirst { it == InsertKind.REVERB }
        if (delay >= 0 && reverb >= 0 && delay > reverb) return "Put Delay before Reverb"
        val tune = live.indexOfFirst { it == InsertKind.TUNE }
        val eq = live.indexOfFirst { it == InsertKind.EQUALIZER }
        if (tune >= 0 && eq >= 0 && tune > eq) return "Put Tune before EQ"
        return null
    }
}
