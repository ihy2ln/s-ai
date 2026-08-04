package com.sai.app

data class PluginInfo(val id: String, val name: String)

object PluginRegistry {
    // No optional instrument/effect plugins exist yet. Future ones register
    // here; the Menu > Plugins screen and PluginSettings already work off
    // this list, so shipping a plugin is just adding an entry.
    val available: List<PluginInfo> = emptyList()
}
