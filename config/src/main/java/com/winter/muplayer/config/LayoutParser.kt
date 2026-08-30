package com.winter.muplayer.config

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON 布局配置文件解析器。
 *
 * ## 格式 v2（对象化 slot）
 *
 * 界面的内容为对象 `{}`，里面不同的名字对应不同 slot：
 * ```json
 * { "screen": { "slot1": ["compose1", "compose2"], "slot2": [...] } }
 * ```
 *
 * 子 slot 继续细分 slot 时，在组件数组内嵌命名子 slot：
 * ```json
 * { "slot1": ["normal-compose1", { "name": "children-slot", "children": ["child-1", "child-2"] }, "normal-compose2"] }
 * ```
 *
 * 若 slot / 容器名与已知 **slot 型组件** id 一致（如 `fp-backdrop`），
 * 解析为 slot 型组件：自身渲染为背景层（fillMaxSize），children 作为前景层叠加。
 * ```json
 * { "full-player": { "fp-backdrop": ["fp-title", "fp-cover"] } }
 * ```
 *
 * ## 组件数组元素格式
 * - 字符串：`"app-name"` → ComponentEntry("app-name", isCustom=false)
 *           `"#my-button"` → ComponentEntry("my-button", isCustom=true)
 *           `"#my-button@my-id"` → ComponentEntry("my-button", cid="my-id", isCustom=true)
 * - 对象（单 key，value 是对象）：
 *   `{ "#button": { "action": "play" } }` → 自定义组件（# 前缀）
 *   `{ "icon": { "icon": "ic_search" } }` → 内置组件（无 # 前缀）
 *   `{ "#icon@search-icon": { "icon": "ic_search" } }` → 带 cid（@ 语法）
 *   cid 只能从 key 的 @ 语法取，value 中声明无效
 *   保留 key（不进 extra）：class / name / children
 * - 对象（单 key，value 是数组）：
 *   `{ "fp-backdrop": [...] }` → key 是 slot 型组件 id → 容器组件
 *   `{ "slotA": [...] }` → 其他名字 → 子 slot 声明（匿名容器）
 * - 对象（name + children）：`{ "name": "children-slot", "children": [...] }` → 命名子 slot 容器
 * - 对象（多 key 或所有 value 都是数组）：`{ "top-row": ["#tab-bar"], "body": ["#playlist"] }`
 *   → 自动视为子 slot 字典，等效于嵌入一个容器组件
 */
object LayoutParser {

    /**
     * 已知的 slot 型（容器）组件 id —— 作为 slot / 容器名出现时解析为容器组件：
     * 自身渲染为背景层（fillMaxSize），children 作为前景层叠加。
     * 当前：`fp-backdrop`（全屏封面模糊背景层）、`pb-backdrop`（迷你播放栏卡片背景层）、
     * `backdrop-blur`（毛玻璃镜像容器：children 双渲染，背景为模糊镜像）。
     */
    val SLOT_COMPONENT_IDS: Set<String> = setOf("fp-backdrop", "pb-backdrop", "backdrop-blur")

    /** 匿名容器组件 id：渲染时未注册 → 背景层透明，仅前景 children 显示 */
    const val ANONYMOUS_CONTAINER = "__slot__"

    fun parseSlotValue(value: Any): List<ComponentEntry> {
        return when (value) {
            is JSONArray -> parseComponents(value)
            is JSONObject -> listOf(parseObjectContainer(value))
            else -> throw IllegalArgumentException("Slot value must be array or object, got: $value")
        }
    }

    private fun parseComponents(arr: JSONArray): List<ComponentEntry> {
        val result = mutableListOf<ComponentEntry>()
        for (i in 0 until arr.length()) {
            val item = arr.get(i)
            when (item) {
                is String -> {
                    val isCustom = item.startsWith("#")
                    val raw = normalizeId(item)
                    if (raw.isBlank()) continue
                    // 支持 "name@cid" 简写
                    val (id, cid) = splitAt(raw, '@')
                    if (id.isNotBlank()) result.add(ComponentEntry(id, cid = cid, isCustom = isCustom))
                }
                is JSONObject -> result.addAll(parseObjectElement(item))
            }
        }
        return result
    }

    /**
     * 解析组件数组中的对象元素：
     * - `{ "name": X, "children": [...] }` → 命名子 slot 容器（X 为 slot 型组件 id 时解析为容器组件）
     * - `{ key: value }`（单 key）→ slot 型组件 / 组件声明 / 子 slot 声明
     * - 多 key → 子 slot 字典（匿名容器）
     */
    private fun parseObjectElement(item: JSONObject): List<ComponentEntry> {
        val keys = item.keys().asSequence().toList()
        if (keys.isEmpty()) return emptyList()

        // ── name + children 命名子 slot：{ "name": "children-slot", "children": [...] } ──
        if (keys.size == 2 && item.has("name") && item.has("children")) {
            val name = item.getString("name").trim()
            if (name.isBlank()) throw IllegalArgumentException("'name' must not be blank in $item")
            val childrenValue = item.get("children")
            return if (name in SLOT_COMPONENT_IDS) {
                // name 是 slot 型组件 id → 解析为容器组件（自身作背景层）
                listOf(ComponentEntry(name, extra = mapOf("children" to childrenMapOf(name, childrenValue))))
            } else {
                // 普通命名子 slot：名字用于 CSS 定位（.name）
                listOf(ComponentEntry(ANONYMOUS_CONTAINER, extra = mapOf("children" to mapOf(name to parseChildrenValue(childrenValue)))))
            }
        }

        // ── 单 key 对象 ──
        val firstKey = keys.first()
        if (keys.size == 1) {
            val value = item.get(firstKey)

            // key 是 slot 型组件 id → 容器组件：{ "fp-backdrop": [...] } / { "fp-backdrop": { ... } }
            if (firstKey in SLOT_COMPONENT_IDS) {
                return listOf(ComponentEntry(firstKey, extra = mapOf("children" to childrenMapOf(firstKey, value))))
            }

            if (value is JSONArray) {
                // 子 slot 声明：{ "slotA": [...] } → 匿名容器，slotA 作为子 slot
                return listOf(ComponentEntry(ANONYMOUS_CONTAINER, extra = mapOf("children" to mapOf(firstKey to parseComponents(value)))))
            }

            if (value is JSONObject) {
                // 组件声明：{ "#icon@search-icon": { "icon": "ic_search" } }
                // cid 只能从 key 的 @ 语法取，不允许在 value 里声明
                val isCustom = firstKey.startsWith("#")
                val raw = normalizeId(firstKey)
                if (raw.isBlank()) return emptyList()
                val (id, cid) = splitAt(raw, '@')
                if (id.isBlank()) return emptyList()
                val extra = mutableMapOf<String, Any?>()
                for (k in value.keys()) {
                    when (k) {
                        "class", "name", "children" -> { /* 保留 key */ }
                        else -> extra[k] = value.get(k)
                    }
                }
                val rawChildren = value.optJSONObject("children")
                if (rawChildren != null) {
                    extra["children"] = parseChildrenSlots(rawChildren)
                }
                return listOf(ComponentEntry(id, cid = cid, extra = extra, isCustom = isCustom))
            }

            throw IllegalArgumentException("Unsupported component object: $item")
        }

        // ── 多 key → 子 slot 字典：{ "top-row": ["#tab-bar"], "body": ["#playlist"] } ──
        val childSlots = parseChildrenSlots(item)
        if (childSlots.isNotEmpty()) {
            return listOf(ComponentEntry(ANONYMOUS_CONTAINER, extra = mapOf("children" to childSlots)))
        }
        return emptyList()
    }

    /**
     * 解析 slot 值（对象形式）→ 单个容器组件。
     * 单 key 且是 slot 型组件 id → 容器组件；否则 → 子 slot 字典匿名容器。
     */
    private fun parseObjectContainer(obj: JSONObject): ComponentEntry {
        val keys = obj.keys().asSequence().toList()
        if (keys.size == 1) {
            val key = keys.first()
            if (key in SLOT_COMPONENT_IDS) {
                return ComponentEntry(key, extra = mapOf("children" to childrenMapOf(key, obj.get(key))))
            }
        }
        return ComponentEntry(ANONYMOUS_CONTAINER, extra = mapOf("children" to parseChildrenSlots(obj)))
    }

    /**
     * children 值 → 子 slot 字典：
     * - 数组 → 包装为单个子 slot，子 slot 名 = 容器组件 id（如 `fp-backdrop` 的 children
     *   数组用 `.fp-backdrop` 定位，与容器 id 同名便于 CSS 记忆）
     * - 对象 → 子 slot 字典；兼容旧格式包装 `{ "children": { ... } }`
     */
    private fun childrenMapOf(containerId: String, value: Any): Map<String, List<ComponentEntry>> = when (value) {
        is JSONArray -> mapOf(containerId to parseComponents(value))
        is JSONObject -> {
            // 兼容旧格式：{ "children": { "slotA": [...] } } → 取 children 对象为子 slot 字典
            val wrapped = value.optJSONObject("children")
            if (wrapped != null && value.length() == 1) parseChildrenSlots(wrapped)
            else parseChildrenSlots(value)
        }
        else -> throw IllegalArgumentException("children must be an array or object, got: $value")
    }

    private fun parseChildrenValue(value: Any): List<ComponentEntry> = when (value) {
        is JSONArray -> parseComponents(value)
        else -> throw IllegalArgumentException("children must be a JSON array, got: $value")
    }

    /**
     * 解析 children 字典：每个 key 映射到一个组件数组，与顶层 slot 格式一致。
     * ```json
     * { "slot-a": ["#comp1"], "slot-b": ["#comp2", "#comp3"] }
     * ```
     */
    private fun parseChildrenSlots(json: JSONObject): Map<String, List<ComponentEntry>> {
        val result = linkedMapOf<String, List<ComponentEntry>>()
        for (key in json.keys()) {
            val value = json.get(key)
            val entries = when (value) {
                is JSONArray -> parseComponents(value)
                else -> throw IllegalArgumentException("Children slot '$key' must be a JSON array")
            }
            result[key] = entries
        }
        return result
    }

    /** 去掉可选的 # 前缀 */
    private fun normalizeId(s: String): String =
        if (s.startsWith("#")) s.substring(1) else s

    /**
     * 按分隔符拆分字符串，返回 (prefix, suffixOrNull)。
     * 如 "icon@search-icon" → ("icon", "search-icon")；"icon" → ("icon", null)
     */
    private fun splitAt(s: String, sep: Char): Pair<String, String?> {
        val idx = s.indexOf(sep)
        return if (idx >= 0) s.substring(0, idx) to s.substring(idx + 1).ifBlank { null }
        else s to null
    }

    fun readFileContent(file: File): String = removeComments(file.readText())

    fun removeComments(json: String): String {
        val sb = StringBuilder(json.length)
        var i = 0
        var inString = false
        while (i < json.length) {
            val c = json[i]
            if (c == '"') {
                // 统计引号前连续反斜杠数量：奇数个 = 转义引号（不切换字符串状态），偶数个 = 字符串边界
                var backslashes = 0
                var j = i - 1
                while (j >= 0 && json[j] == '\\') { backslashes++; j-- }
                if (backslashes % 2 == 0) inString = !inString
            }
            if (!inString && c == '/' && i + 1 < json.length) {
                val next = json[i + 1]
                if (next == '/') {
                    i += 2; while (i < json.length && json[i] != '\n') i++; continue
                } else if (next == '*') {
                    i += 2
                    while (i + 1 < json.length && !(json[i] == '*' && json[i + 1] == '/')) i++
                    i += 2; continue
                }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }
}
