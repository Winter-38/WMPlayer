package com.winter.muplayer.config

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON 布局配置文件解析器。
 *
 * 每个 slot value 是组件数组，每项支持两种格式：
 * - 字符串：`"app-name"` → ComponentEntry("app-name", isCustom=false)
 *           `"#my-button"` → ComponentEntry("my-button", isCustom=true)
 *           `"#my-button@my-id"` → ComponentEntry("my-button", cid="my-id", isCustom=true)
 * - 对象（单 key，value 是对象）：
 *   `{ "#button": { "action": "play" } }` → 自定义组件（# 前缀）
 *   `{ "icon": { "icon": "ic_search" } }` → 内置组件（无 # 前缀）
 *   `{ "#icon@search-icon": { "icon": "ic_search" } }` → 带 cid（@ 语法）
 *   cid 只能从 key 的 @ 语法取，value 中声明无效
 *   保留 key（不进 extra）：class / name / children
 * - 对象（多 key 或所有 value 都是数组）：
 *   `{ "top-row": ["#tab-bar"], "body": ["#playlist"] }`
 *   → 自动视为子 slot 字典，等效于嵌入一个容器组件
 */
object LayoutParser {

    fun parseSlotValue(value: Any): List<ComponentEntry> {
        val arr = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("children")
                ?: throw IllegalArgumentException("Slot object must have a \"children\" array")
            else -> throw IllegalArgumentException("Slot value must be array or object, got: $value")
        }
        return parseComponents(arr)
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
                is JSONObject -> {
                    val keys = item.keys().asSequence().toList()
                    val firstKey = keys.firstOrNull()

                    if (keys.size == 1 && firstKey != null) {
                        val value = item.get(firstKey)
                        if (value is JSONObject) {
                            // 组件声明：{ "#icon@search-icon": { "icon": "ic_search" } }
                            // cid 只能从 key 的 @ 语法取，不允许在 value 里声明
                            val isCustom = firstKey.startsWith("#")
                            val raw = normalizeId(firstKey)
                            if (raw.isBlank()) continue
                            val (id, cid) = splitAt(raw, '@')
                            if (id.isBlank()) continue
                            val extra = mutableMapOf<String, Any?>()
                            for (k in value.keys()) {
                                when (k) {
                                    "class", "name", "children" -> { /* 保留 key */ }
                                    else -> extra[k] = value.get(k)
                                }
                            }
                            val rawChildren = value.optJSONObject("children")
                            if (rawChildren != null) {
                                extra["children"] = parseChildrenMap(rawChildren)
                            }
                            result.add(ComponentEntry(id, cid = cid, extra = extra, isCustom = isCustom))
                        } else {
                            // value 不是对象 → 尝试作为子 slot 字典
                            val childSlots = parseChildrenMap(item)
                            if (childSlots.isNotEmpty()) {
                                result.add(ComponentEntry("__slot__", extra = mapOf("children" to childSlots)))
                            }
                        }
                    } else {
                        // 多 key → 子 slot 字典：{ "top-row": ["#tab-bar"], "body": ["#playlist"] }
                        val childSlots = parseChildrenMap(item)
                        if (childSlots.isNotEmpty()) {
                            result.add(ComponentEntry("__slot__", extra = mapOf("children" to childSlots)))
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * 解析 children 字典：每个 key 映射到一个组件数组，与顶层 slot 格式一致。
     * ```json
     * { "slot-a": ["#comp1"], "slot-b": ["#comp2", "#comp3"] }
     * ```
     */
    private fun parseChildrenMap(json: JSONObject): Map<String, List<ComponentEntry>> {
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