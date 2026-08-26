package com.sai.app

data class PluginInfo(val id: String, val name: String)

object PluginRegistry {
    // No optional instrument/effect plugins exist yet. The M → Plugins item is
    // hidden while this list is empty; adding an entry here makes the toggle
    // screen appear (PluginSettings already works off this list).
    val available: List<PluginInfo> = emptyList()
}
