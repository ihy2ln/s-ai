package com.sai.app

import com.sai.core.plugin.PluginCatalog
import com.sai.core.plugin.PluginDescriptor
import com.sai.core.plugin.PluginRole

/** Maps catalog entries onto Home [ModuleType] values without replacing built-in modules. */
object PluginModules {

    fun descriptorFor(type: ModuleType): PluginDescriptor? =
        PluginCatalog.all.firstOrNull { it.homeModule == type.name }

    fun moduleType(plugin: PluginDescriptor): ModuleType? {
        val name = plugin.homeModule ?: return null
        return try {
            ModuleType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun infoList(): List<PluginInfo> = PluginCatalog.toggleable().map { plugin ->
        PluginInfo(plugin.id, "${plugin.name}  ${plugin.format.badge}")
    }
}

fun PluginDescriptor.roleLabel(): String = when (role) {
    PluginRole.HOME -> "Home"
    PluginRole.INSTRUMENT -> "Instrument"
    PluginRole.EFFECT -> "Effect"
}
