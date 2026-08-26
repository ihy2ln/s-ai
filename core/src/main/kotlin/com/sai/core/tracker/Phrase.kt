package com.sai.core.tracker

data class Step(
    val note: Int? = null,
    val instrument: Int? = null,
    val volume: Int? = null,
) {
    val isEmpty: Boolean get() = note == null && instrument == null
}

data class Phrase(val steps: List<Step>) {
    init {
        require(steps.size == MAX_STEPS) { "Phrase must have exactly $MAX_STEPS steps" }
    }

    companion object {
        const val MAX_STEPS = 32
        const val DEFAULT_LENGTH = 16
        val LENGTHS = listOf(8, 16, 32)

        fun empty() = Phrase(List(MAX_STEPS) { Step() })

        /** Pads or trims [steps] so a Phrase is always [MAX_STEPS] long (older projects stored 16). */
        fun fromSteps(steps: List<Step>): Phrase =
            Phrase(List(MAX_STEPS) { i -> steps.getOrNull(i) ?: Step() })

        fun coerceLength(value: Int): Int = when {
            value <= 8 -> 8
            value <= 16 -> 16
            else -> 32
        }
    }
}
