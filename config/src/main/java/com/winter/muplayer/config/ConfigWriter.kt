package com.winter.muplayer.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置序列化器 —— 把内存中的布局 / 样式模型写回磁盘格式（main.json / styles.css）。
 *
 * 与 [LayoutParser] / [CssParser] 互为逆操作：布局编辑器修改模型后调用本类序列化，
 * 再经 [StyleConfigLoader.reload] 重新解析生效。
 *
 * 布局序列化输出规范形式（保证能被 [LayoutParser] 原样读回）：
 * - `main` / `full-player` 使用对象形式（slot 名 → 组件数组）；
 * - 容器组件（`fp-backdrop` 等 slot 型组件）输出 `{ "id": { "子slot名": [...] } }`；
 * - 匿名容器（子 slot 字典，含命名子 slot）输出多 key 对象 `{ "slotA": [...], "slotB": [...] }`；
 * - 组件无 extra 时输出字符串 `"id@cid"`，带 extra 时输出对象 `{ "id@cid": { ...extra } }`；
 * - 自定义组件（isCustom）保留 `#` 前缀。
 *
 * 样式序列化输出单行块格式，选择器顺序稳定（区域在前、组件在后、组内字母序），
 * 属性顺序按常用度排序，便于阅读与 diff。
 */
object ConfigWriter {

    // ──────────────────────────── 布局 → main.json ────────────────────────────

    /** 序列化完整布局为 main.json 文本（含自定义组件定义）。 */
    fun layoutToJson(layout: ComponentLayout): String {
        val root = JSONObject()

        // 自定义组件定义：#name → 属性字典
        if (layout.customComponents.isNotEmpty()) {
            layout.customComponents.forEach { (name, props) ->
                root.put("#$name", JSONObject().apply {
                    props.forEach { (k, v) -> put(k, toJsonValue(v)) }
                })
            }
        }

        // 主界面 slots
        if (layout.slots.isNotEmpty()) {
            val main = JSONObject()
            layout.slots.forEach { (slotName, entries) ->
                main.put(slotName, slotsValueJson(entries))
            }
            root.put("main", main)
        }

        // 全屏播放器 slots
        layout.fullPlayerSlots.forEach { (slotName, entries) ->
            root.put(slotName, slotsValueJson(entries))
        }

        return root.toString(2)
    }

    /**
     * slot 值序列化：
     * - 单个容器条目 → 对象形式（`{ "fp-backdrop": { ... } }` 或子 slot 字典），更贴近默认模板；
     * - 其余 → 组件数组。
     */
    private fun slotsValueJson(entries: List<ComponentEntry>): Any {
        if (entries.size == 1 && entries[0].id != LayoutParser.ANONYMOUS_CONTAINER &&
            (entries[0].id in LayoutParser.SLOT_COMPONENT_IDS || entries[0].extra["children"] is Map<*, *>)
        ) {
            return entryToJson(entries[0])
        }
        return JSONArray().apply { entries.forEach { put(entryToJson(it)) } }
    }

    /** 单个组件条目 → JSON 值（字符串或对象）。 */
    fun entryToJson(entry: ComponentEntry): Any {
        val label = if (entry.cid != null) "${entry.id}@${entry.cid}" else entry.id
        return when {
            // 匿名容器（子 slot 字典 / 命名子 slot）：{ "slotA": [...], "slotB": [...] }
            entry.id == LayoutParser.ANONYMOUS_CONTAINER -> {
                JSONObject().apply {
                    childSlotsOf(entry).forEach { (name, list) ->
                        put(name, JSONArray().apply { list.forEach { put(entryToJson(it)) } })
                    }
                }
            }
            // slot 型容器组件：{ "fp-backdrop": { "子slot": [...] } }
            entry.id in LayoutParser.SLOT_COMPONENT_IDS -> {
                JSONObject().apply {
                    put(entry.id, JSONObject().apply {
                        childSlotsOf(entry).forEach { (name, list) ->
                            put(name, JSONArray().apply { list.forEach { put(entryToJson(it)) } })
                        }
                    })
                }
            }
            // 无 extra → 字符串
            entry.extra.isEmpty() -> customPrefix(entry) + label
            // 带 extra → 对象
            else -> JSONObject().apply {
                put(customPrefix(entry) + label, toJsonValue(entry.extra))
            }
        }
    }

    private fun childSlotsOf(entry: ComponentEntry): Map<String, List<ComponentEntry>> {
        @Suppress("UNCHECKED_CAST")
        return entry.extra["children"] as? Map<String, List<ComponentEntry>> ?: emptyMap()
    }

    private fun customPrefix(entry: ComponentEntry): String = if (entry.isCustom) "#" else ""

    /**
     * extra 值 → JSON 值。核心是 `children`（Map<String, List<ComponentEntry>>）
     * 递归序列化为子 slot 对象；其余值原样透传。
     */
    private fun toJsonValue(value: Any?): Any = when (value) {
        is ComponentEntry -> entryToJson(value)
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (k, v) ->
                put(
                    k.toString(),
                    when (v) {
                        is List<*> -> JSONArray().apply {
                            v.forEach { item ->
                                put(if (item is ComponentEntry) entryToJson(item) else toJsonValue(item))
                            }
                        }
                        else -> toJsonValue(v)
                    },
                )
            }
        }
        is List<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
        is JSONObject, is JSONArray -> value
        is Boolean, is Int, is Long, is Double, is Float -> value
        is String -> value
        null -> JSONObject.NULL
        else -> value.toString()
    }

    // ──────────────────────────── 样式 → styles.css ────────────────────────────

    /** CSS 属性输出顺序（未知属性按字母序排在最后）。 */
    private val PROP_ORDER: List<String> = listOf(
        "arrange", "weight", "size", "width", "height",
        "color", "background-color", "border-radius",
        "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "gap", "font-size",
        "align", "align-self", "justify-content", "content-align",
        "opacity", "scale", "rotate", "overflow",
        "render-style", "blur-radius",
        "liquid-edge", "liquid-refraction", "liquid-opacity",
        "liquid-specular", "liquid-shininess", "liquid-rim",
    )

    /** 序列化样式表为 styles.css 文本（选择器顺序稳定：区域在前、组件在后、组内字母序）。 */
    fun cssTableToText(table: CssRuleTable): String {
        val sb = StringBuilder()
        val rules = table.rules.toList().sortedWith(compareBy(
            { selectorRank(it.first) },
            { it.first },
        ))
        for ((selector, props) in rules) {
            if (props.isEmpty()) continue
            sb.append(selector).append(" {\n")
            val orderedProps = props.toList().sortedWith(compareBy(
                { PROP_ORDER.indexOf(it.first).let { idx -> if (idx < 0) Int.MAX_VALUE else idx } },
                { it.first },
            ))
            for ((key, value) in orderedProps) {
                sb.append("  ").append(key).append(": ").append(value).append(";\n")
            }
            sb.append("}\n\n")
        }
        return if (sb.isEmpty()) "" else sb.toString().trimEnd() + "\n"
    }

    /** 选择器排序：.main / .full-player 最前，其次 .区域，最后 #组件。 */
    private fun selectorRank(selector: String): Int = when {
        selector == ".main" || selector == ".full-player" -> 0
        selector.startsWith(".") -> 1
        selector.startsWith("#") -> 2
        else -> 3
    }
}
