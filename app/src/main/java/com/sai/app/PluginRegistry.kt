package com.sai.app

import com.sai.core.plugin.PluginCatalog

data class PluginInfo(val id: String, val name: String)

object PluginRegistry {
    /** Optional instrument and effect plugins. Built-in Home modules are not listed here;
     *  they always stay available from Add Module. */
    val available: List<PluginInfo> = PluginModules.infoList()

    fun isKnown(id: String): Boolean = PluginCatalog.byId(id) != null
}
