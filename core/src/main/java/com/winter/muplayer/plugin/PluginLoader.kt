package com.winter.muplayer.plugin

/**
 * @deprecated 插件加载功能已迁移至 [com.winter.muplayer.plugin_loader.PluginLoader]（Shadow 架构）。
 * 此文件仅保留用于编译兼容，将在下个主版本中移除。
 */
@Deprecated(
    message = "Use com.winter.muplayer.plugin_loader.PluginLoader instead (Shadow architecture)",
    replaceWith = ReplaceWith("com.winter.muplayer.plugin_loader.PluginLoader")
)
class PluginLoader