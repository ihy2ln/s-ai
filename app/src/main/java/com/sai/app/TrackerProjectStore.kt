package com.sai.app

import android.content.Context

object TrackerProjectStore {
    @Volatile private var instance: TrackerProject? = null

    fun get(context: Context): TrackerProject {
        return instance ?: synchronized(this) {
            instance ?: TrackerProject(context.applicationContext).also { instance = it }
        }
    }
}
