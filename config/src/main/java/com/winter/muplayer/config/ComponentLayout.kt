package com.winter.muplayer.config

/**
 * 组件条目 —— 一个组件实例在 slot 中的声明。
 * @param id       组件注册 ID（字符串简写或 JSON key 去掉 # 前缀后的值）
 * @param cid      可选实例标识，用于 CSS 精准匹配（`#<cid>` 规则覆盖 `#<id>` 规则）
 * @param extra    组件行为/配置参数，由 JSON 对象属性指定
 * @param isCustom 原始写法是否带 `#` 前缀（未找到自定义定义时回退内置组件并输出警告）
 */
data class ComponentEntry(
    val id: String,
    val cid: String? = null,
    val extra: Map<String, Any?> = emptyMap(),
    val isCustom: Boolean = false,
)

/**
 * 组件插槽配置 —— JSON 根级 key 即为 slot 名。
 *
 * 每个 slot 的 value 是组件数组，每项可以是：
 * - 字符串：`"#app-name"`（不带 extra）
 * - 对象：`{ "#button": { "action": "play" } }`（extra 为 cid/class 外的属性）
 *
 * 示例：
 *   {
 *     "app-top": ["#app-name", "#search-button", "#setting-button", "#search-bar"],
 *     "app-center": ["#tab-bar", "#sort", "#playlist"],
 *     "app-bottom": ["#playbar"]
 *   }
 */
data class ComponentLayout(
    val slots: Map<String, List<ComponentEntry>> = defaultSlots,
    /** 自定义组件定义 —— JSON 中以 `#name` 为 key 的条目 */
    val customComponents: Map<String, Map<String, Any?>> = emptyMap(),
    /** 全屏播放器 slot 定义 —— fp-slots JSON key 解析至此，JSON 未定义时回退默认值 */
    val fullPlayerSlots: Map<String, List<ComponentEntry>> = defaultFullPlayerSlots,
) {
    companion object {
        val defaultSlots: Map<String, List<ComponentEntry>> = mapOf(
            "app-top" to listOf(
                ComponentEntry("app-name"),
                ComponentEntry("spacer"),
                ComponentEntry("search-button"),
                ComponentEntry("setting-button"),
            ),
            "app-center" to listOf(
                ComponentEntry("tab-bar"),
                ComponentEntry("sort"),
                ComponentEntry("playlist"),
            ),
            "app-bottom" to listOf(ComponentEntry("playbar")),
        )

        /**
         * 全屏播放器默认 slot 定义 —— 由细分组件组装：
         * - `fp-backdrop` 作背景层容器（children 前景层叠加其上，封面模糊背景）
         * - 前景层：标题与歌手+专辑置于左上角（content 顶部、Text 默认左对齐），
         *   随后依次为 主封面 / 进度条 / 播放操控按钮
         */
        val defaultFullPlayerSlots: Map<String, List<ComponentEntry>> = mapOf(
            "main" to listOf(
                ComponentEntry(
                    id = "fp-backdrop",
                    extra = mapOf(
                        "children" to mapOf(
                            "content" to listOf(
                                ComponentEntry("fp-track-title"),
                                ComponentEntry("fp-track-subtitle"),
                                ComponentEntry("fp-cover"),
                                ComponentEntry("fp-progress"),
                                ComponentEntry("controls-row"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
