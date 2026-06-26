package com.winter.muplayer.plugin

/**
 * @deprecated 插件管理功能已迁移至 [com.winter.muplayer.plugin_manager.PluginManager]（Shadow 架构）。
 * 此文件仅保留用于编译兼容，将在下个主版本中移除。
 */
@Deprecated(
    message = "Use com.winter.muplayer.plugin_manager.PluginManager instead (Shadow architecture)",
    replaceWith = ReplaceWith("com.winter.muplayer.plugin_manager.PluginManager")
)
class PluginManager