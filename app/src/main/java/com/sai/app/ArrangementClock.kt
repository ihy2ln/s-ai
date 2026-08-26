package com.sai.app

/** Live arrangement playhead for Playlist and other screens. */
object ArrangementClock {
    @Volatile var globalStep: Int = -1
        private set
    @Volatile var pattern: Int = -1
        private set
    @Volatile var localStep: Int = -1
        private set

    fun set(globalStep: Int, pattern: Int, localStep: Int) {
        this.globalStep = globalStep
        this.pattern = pattern
        this.localStep = localStep
    }

    fun clear() {
        set(-1, -1, -1)
    }
}
