package com.winter.muplayer.plugin.bridge

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lua 表 ↔ JSON 转换（json.parse / json.stringify 的实现）。
 *
 * - [stringify]：Lua 数组表（连续整数键 1..n，无其他键）→ JSON 数组；
 *   其余按对象处理（数字键跳过）；
 * - [parse]：JSON 对象 → Lua 表、JSON 数组 → 1..n 连续键表。
 */
object LuaJson {

    /** Lua 值 → JSON 值（用于 stringify）。 */
    fun toJson(value: LuaValue): Any = when {
        value.isnil() -> JSONObject.NULL
        // 注意顺序：LuaJ 的 LuaNumber.isstring() 返回 true（数字可作字符串），
        // 必须先把数字判定放在字符串之前，否则数字会被序列化成字符串
        value.isnumber() -> value.todouble()
        value.isstring() -> value.tojstring()
        value.isboolean() -> value.toboolean()
        value.istable() -> {
            val table = value.checktable()
            if (isArrayTable(table)) {
                JSONArray().also { arr ->
                    var i = 1
                    while (!table.get(i).isnil()) {
                        arr.put(toJson(table.get(i)))
                        i++
                    }
                }
            } else {
                JSONObject().also { obj ->
                    for (key in table.keys()) {
                        val k = key.tojstring()
                        if (k.toIntOrNull() != null) continue // 数字键在对象模式下跳过
                        obj.put(k, toJson(table.get(key)))
                    }
                }
            }
        }
        else -> JSONObject.NULL
    }

    /** JSON 值 → Lua 值（用于 parse）。 */
    fun toLua(value: Any?): LuaValue = when {
        value == null || value == JSONObject.NULL -> LuaValue.NIL
        value is String -> LuaValue.valueOf(value)
        value is Boolean -> LuaValue.valueOf(value)
        value is Int -> LuaValue.valueOf(value)
        value is Long -> LuaValue.valueOf(value.toDouble())
        value is Double -> LuaValue.valueOf(value)
        value is JSONObject -> {
            val table = LuaTable()
            val it = value.keys()
            while (it.hasNext()) {
                val k = it.next()
                table.set(k, toLua(value.opt(k)))
            }
            table
        }
        value is JSONArray -> {
            val table = LuaTable()
            for (i in 0 until value.length()) table.set(i + 1, toLua(value.opt(i)))
            table
        }
        else -> LuaValue.NIL
    }

    /**
     * 判断 Lua 表是否为数组：所有键均为正整数、1..n 连续（无空洞）、
     * 无 n+1 键、且无字符串等其它键。空表视为对象。
     */
    fun isArrayTable(table: LuaTable): Boolean {
        var size = 0
        var hasOtherKeys = false
        for (key in table.keys()) {
            if (key.isnumber() && key.toint() > 0 && key.todouble() == key.toint().toDouble()) {
                size++
            } else {
                hasOtherKeys = true
            }
        }
        if (size == 0 || hasOtherKeys) return false
        for (i in 1..size) {
            if (table.get(i).isnil()) return false // 有空洞 → 非数组
        }
        return table.get(size + 1).isnil() // 存在 size+1 键 → 键集不连续
    }
}
