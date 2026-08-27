package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.bridge.PluginSession

/**
 * 插件 UI 桥接：宿主侧（ui 模块）实现本接口并注入 [LuaPluginManager]，
 * 在插件加载/卸载时同步插件注册的组件/界面/widget 到全局渲染层。
 *
 * 依赖方向：plugin 定义接口（无 UI 依赖），ui 实现并注入 —— 避免反向依赖。
 */
interface PluginUiBridge {

    /** 插件加载完成（导出函数注册后）调用：宿主据此注册该插件的 UI 元素。 */
    fun onPluginLoaded(session: PluginSession)

    /** 插件运行时通过 wm.ui 注册/反注册组件后调用：宿主重新同步该插件的组件渲染器。 */
    fun onPluginUiChanged(pluginId: String)

    /** 插件卸载时调用：宿主清理该插件的 UI 元素与打开状态。 */
    fun onPluginUnloaded(pluginId: String)
}
