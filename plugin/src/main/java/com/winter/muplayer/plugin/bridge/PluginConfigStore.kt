package com.winter.muplayer.plugin.bridge

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.json.JSONObject
import java.io.File

/**
 * 插件持久化配置存储（`plugin.getConfig` / `plugin.setConfig`）。
 *
 * 每个插件一个 JSON 文件（`{configDir}/{pluginId}.json`），支持
 * string / number / boolean / table（table 序列化为 JSON 对象）。
 * 用文件而非 SharedPreferences 实现，便于 JVM 单元测试。
 */
class PluginConfigStore(
    configDir: File,
    pluginId: String
) {
    private val file = File(configDir, "$pluginId.json")
    private val data: JSONObject = if (file.exists()) {
        try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    } else {
        JSONObject()
    }

    /** 读取配置项；不存在返回 NIL。 */
    fun get(key: String): LuaValue =
        luaFromJson(data.opt(key)) ?: LuaValue.NIL

    /** 写入配置项，返回旧值（供 onConfigChanged 使用）。 */
    fun set(key: String, value: LuaValue): LuaValue {
        val old = get(key)
        data.put(key, jsonFromLua(value))
        persist()
        return old
    }

    /** 移除配置项。 */
    fun remove(key: String) {
        if (data.has(key)) {
            data.remove(key)
            persist()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(data.toString())
    }

    // ==================== JSON ↔ LuaValue ====================

    private fun jsonFromLua(v: LuaValue): Any = when {
        // 注意顺序：LuaJ 的 LuaNumber.isstring() 返回 true，数字判定必须在前
        v.isnumber() -> v.todouble()
        v.isstring() -> v.tojstring()
        v.isboolean() -> v.toboolean()
        v.istable() -> {
            val table = v.checktable()
            val obj = JSONObject()
            for (key in table.keys()) {
                val k = key.tojstring()
                if (k.toIntOrNull() != null) continue // 跳过数组部分
                obj.put(k, jsonFromLua(table.get(key)))
            }
            obj
        }
        else -> ""
    }

    private fun luaFromJson(value: Any?): LuaValue? = when (value) {
        null -> LuaValue.NIL
        is String -> LuaValue.valueOf(value)
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Double -> LuaValue.valueOf(value)
        is JSONObject -> {
            val table = LuaTable()
            val it = value.keys()
            while (it.hasNext()) {
                val key = it.next()
                table.set(key, luaFromJson(value.opt(key)) ?: LuaValue.NIL)
            }
            table
        }
        else -> LuaValue.NIL
    }
}
