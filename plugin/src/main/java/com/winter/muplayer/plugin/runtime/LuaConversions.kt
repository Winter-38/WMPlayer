package com.winter.muplayer.plugin.runtime

import com.winter.muplayer.plugin.model.PluginEvent
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs

/** 宿主值 ↔ Lua 值转换工具。 */
object LuaConversions {

    /** 把事件转换为 Lua 参数：`(name, payloadTable)`。 */
    fun toVarargs(event: PluginEvent): Varargs =
        LuaValue.varargsOf(arrayOf(LuaValue.valueOf(event.name), toLuaValue(event.payload)))

    /** 宿主值转 Lua 值：标量 / Map / List / null。 */
    fun toLuaValue(value: Any?): LuaValue = when (value) {
        null -> LuaValue.NIL
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Double -> LuaValue.valueOf(value)
        is Float -> LuaValue.valueOf(value.toDouble())
        is String -> LuaValue.valueOf(value)
        is Map<*, *> -> LuaTable().also { table ->
            value.forEach { (k, v) -> table.set(k.toString(), toLuaValue(v)) }
        }
        is List<*> -> LuaTable().also { table ->
            value.forEachIndexed { i, v -> table.set(i + 1, toLuaValue(v)) }
        }
        else -> LuaValue.valueOf(value.toString())
    }
}
