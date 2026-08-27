package com.winter.muplayer.plugin.bridge

import org.luaj.vm2.LuaFunction
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件 UI 动作 —— 组件手势（点按/长按/滑动）可执行三种动作之一：
 * - [Call]：执行一个 Lua 函数；
 * - [OpenPage]：唤起该插件注册的界面；
 * - [OpenWidget]：唤起该插件注册的 widget 浮层。
 *
 * Lua 侧写法：`onClick = function() end`、`onClick = { page = "pageId" }`、
 * `onClick = { widget = "widgetId" }`、`onClick = { call = function() end }`。
 */
sealed class UiAction {
    class Call(val fn: LuaFunction) : UiAction()
    class OpenPage(val pageId: String) : UiAction()
    class OpenWidget(val widgetId: String) : UiAction()
}

/**
 * 插件注册的组件配置。
 *
 * @param isSlot true 为 slot 型组件（参考 fp-backdrop：自身作背景层，children 由布局 JSON 承载）；
 *               false 为普通组件（icon/标题 + 手势）。
 */
class UiComponentConfig(
    val id: String,
    val title: String,
    val icon: String?,
    val onClick: UiAction?,
    val onLongClick: UiAction?,
    val onSwipe: UiAction?,
    val isSlot: Boolean,
)

/** 插件注册的界面：布局 JSON 存放在插件 zip 内（layoutPath 为 zip 内相对路径）。 */
class UiPageConfig(
    val id: String,
    val title: String,
    val layoutPath: String,
)

/**
 * 插件注册的 widget（轻量浮层）。
 *
 * @param type 浮层形态：`dropdown` / `bottom_sheet` / `dialog`
 *             （dropdown 简化为居中圆角卡片浮层）
 */
class UiWidgetConfig(
    val id: String,
    val title: String,
    val type: String,
    val layoutPath: String,
)

/**
 * 单个插件的 UI 注册表（每 PluginSession 一个）。
 * 仅存储注册数据（含 Lua 函数引用）；实际渲染由宿主侧 PluginUiHost 完成。
 */
class PluginUiRegistry {

    /** 普通组件与 slot 型组件（isSlot 区分） */
    val components: MutableMap<String, UiComponentConfig> = ConcurrentHashMap()

    val pages: MutableMap<String, UiPageConfig> = LinkedHashMap()

    val widgets: MutableMap<String, UiWidgetConfig> = LinkedHashMap()

    fun clear() {
        components.clear()
        pages.clear()
        widgets.clear()
    }

    /** 注册总数（诊断用）。 */
    val size: Int get() = components.size + pages.size + widgets.size
}
