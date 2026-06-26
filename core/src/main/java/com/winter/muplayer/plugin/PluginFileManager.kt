package com.winter.muplayer.plugin

/**
 * @deprecated 文件管理功能已不再需要。插件 APK 现由 [com.winter.muplayer.plugin_manager.PluginManager] 管理。
 * 此文件仅保留用于编译兼容，将在下个主版本中移除。
 */
@Deprecated(
    message = "No longer needed; plugin APKs are managed by PluginManager (Shadow architecture)",
    replaceWith = ReplaceWith("com.winter.muplayer.plugin_manager.PluginManager")
)
class PluginFileManager