package com.winter.muplayer.base_ui.ui.config

import androidx.compose.runtime.compositionLocalOf

/**
 * 组件条目 —— 一个组件实例在 slot 中的声明。
 * @param id       组件注册 ID（无 # 前缀）
 * @param extra    组件行为/配置参数，由 JSON 对象属性指定
 * @param isCustom 原始写法是否带 `#` 前缀（未找到自定义定义时回退内置组件并输出警告）
 */
data class ComponentEntry(
    val id: String,
    val extra: Map<String, Any?> = emptyMap(),
    val isCustom: Boolean = false,
)

/**
 * 组件插槽配置 —— JSON 根级 key 即为 slot 名。
 *
 * 每个 slot 的 value 是组件数组，每项可以是：
 * - 字符串：`"#app-name"`（不带 extra）
 * - 对象：`{ "type": "#button", "action": "play" }`（extra 为 type/class 外的属性）
 *
 * 示例：
 *   {
 *     "app-top": ["#app-name", "#search-button", "#setting-button", "#search-bar"],
 *     "app-center": ["#playlist"],
 *     "app-bottom": ["#playbar"]
 *   }
 */
/**
 * 自定义组件定义 —— 用户通过 JSON 声明的组件。
 * @param icon drawable 资源名，如 `"ic_search"`
 * @param onClick 点击行为标识，如 `"toggleSearch"`、`"openSettings"`
 */
data class CustomComponentDef(
    val icon: String? = null,
    val onClick: String? = null,
)

data class ComponentLayout(
    val slots: Map<String, List<ComponentEntry>> = defaultSlots,
    /** 自定义组件定义 —— JSON 中以 `#name` 为 key 的条目 */
    val customComponents: Map<String, Map<String, Any?>> = emptyMap(),
) {
    companion object {
        val defaultSlots: Map<String, List<ComponentEntry>> = mapOf(
            "app-top" to listOf(
                ComponentEntry("app-name"),
                ComponentEntry("search-button"),
                ComponentEntry("setting-button"),
            ),
            "app-center" to listOf(ComponentEntry("playlist")),
            "app-bottom" to listOf(ComponentEntry("playbar")),
        )
    }
}

val LocalComponentLayout = compositionLocalOf { ComponentLayout() }
