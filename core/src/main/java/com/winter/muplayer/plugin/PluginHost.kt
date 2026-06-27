package com.winter.muplayer.plugin

/**
 * @deprecated 插件系统已重写为 Shadow 架构。
 *
 * 旧的 ContentProvider IPC 方案已被 DexClassLoader 动态加载方案取代。
 * 请使用 [com.winter.muplayer.plugin_manager.ShadowPluginHost] 替代。
 *
 * ## 迁移指南
 * - `discover()` → [com.winter.muplayer.plugin_manager.ShadowPluginHost.loadAll]
 * - `broadcast()` → 插件通过 [com.winter.muplayer.plugin_runtime.IPlayerHost] 直接访问
 * - `refreshSlots()` → [com.winter.muplayer.plugin_manager.ShadowPluginHost.getSlotViews]
 */
@Deprecated(
    message = "Use com.winter.muplayer.plugin_manager.ShadowPluginHost instead (Shadow architecture)",
    replaceWith = ReplaceWith(
        "com.winter.muplayer.plugin_manager.ShadowPluginHost",
        "com.winter.muplayer.plugin_manager.ShadowPluginHost"
    )
)
class PluginHost
