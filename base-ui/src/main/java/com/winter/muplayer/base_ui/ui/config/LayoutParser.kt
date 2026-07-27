package com.winter.muplayer.base_ui.ui.config

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON 布局配置文件解析器。
 *
 * 每个 slot value 是组件数组。每项支持两种格式：
 * - 字符串：`"#app-name"` → ComponentEntry("app-name")
 * - 对象（有 type）：
 *   `{ "type": "#button", "action": "play", "label": "Go" }`
 *   → ComponentEntry("button", {"action": "play", "label": "Go"})
 *   其中 type / class / name / children 为保留 key，不进入 extra
 * - 对象（无 type，且所有 value 都是数组）：
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
                    val id = normalizeId(item)
                    if (id.isNotBlank()) result.add(ComponentEntry(id, isCustom = item.startsWith("#")))
                }
                is JSONObject -> {
                    val type = item.optString("type", "").takeIf { it.isNotBlank() }
                    if (type == null) {
                        // 无 type → 尝试作为子 slot 字典（每个 value 都应是数组）
                        val childSlots = parseChildrenMap(item)
                        if (childSlots.isNotEmpty()) {
                            result.add(ComponentEntry("__slot__", mapOf("children" to childSlots)))
                        }
                    } else {
                        // 有 type → 组件对象
                        val id = normalizeId(type)
                        if (id.isBlank()) continue
                        val extra = mutableMapOf<String, Any?>()
                        for (key in item.keys()) {
                            if (key in setOf("type", "class", "name", "children")) continue
                            extra[key] = item.get(key)
                        }
                        // 解析 children 嵌套子 slot（与顶层 slot 格式一致）
                        val rawChildren = item.optJSONObject("children")
                        if (rawChildren != null) {
                            extra["children"] = parseChildrenMap(rawChildren)
                        }
                        result.add(ComponentEntry(id, extra))
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

    fun readFileContent(file: File): String = removeComments(file.readText())

    fun removeComments(json: String): String {
        val sb = StringBuilder(json.length)
        var i = 0
        var inString = false
        while (i < json.length) {
            val c = json[i]
            if (c == '"' && (i == 0 || json[i - 1] != '\\')) inString = !inString
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
