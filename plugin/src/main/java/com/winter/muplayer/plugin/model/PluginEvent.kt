package com.winter.muplayer.plugin.model

/**
 * 插件事件：由宿主（或其它插件）发出，事件驱动插件通过导出的同名函数响应。
 *
 * @param name    事件名，例如 "onTrackChanged"。约定与插件导出函数名一致。
 * @param payload 事件负载，标量 / Map / List / null 会被转换为 Lua 值。
 */
data class PluginEvent(
    val name: String,
    val payload: Map<String, Any?> = emptyMap()
)
