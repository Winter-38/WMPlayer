package com.winter.muplayer.plugin

import com.winter.muplayer.plugin_api.MusicPlugin

/**
 * 管理已加载的插件实例。
 * 所有方法 **仅在主线程调用**，无需额外同步。
 */
object PluginManager {

    data class LoadedPlugin(val plugin: MusicPlugin, val dexFileName: String)

    // 仅在主线程访问，无并发风险
    private val _loadedPlugins = mutableListOf<LoadedPlugin>()

    val plugins: List<MusicPlugin> get() = _loadedPlugins.map { it.plugin }

    fun register(plugin: MusicPlugin, dexFileName: String) {
        _loadedPlugins.add(LoadedPlugin(plugin, dexFileName))
    }

    fun unregister(plugin: MusicPlugin) {
        _loadedPlugins.removeAll { it.plugin == plugin }
    }

    fun getPluginByDexFileName(dexFileName: String): MusicPlugin? {
        return _loadedPlugins.find { it.dexFileName == dexFileName }?.plugin
    }

    fun getLoadedPlugins(): List<LoadedPlugin> = _loadedPlugins.toList()

    fun clear() {
        _loadedPlugins.forEach { it.plugin.onUnload() }
        _loadedPlugins.clear()
    }
}