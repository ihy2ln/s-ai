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
        require(steps.size == STEP_COUNT) { "Phrase must have exactly $STEP_COUNT steps" }
    }

    companion object {
        const val STEP_COUNT = 16
        fun empty() = Phrase(List(STEP_COUNT) { Step() })
    }
}
