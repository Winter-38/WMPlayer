package com.winter.muplayer.plugin.registry

import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 插件导出函数表：插件加载时把入口返回的导出表快照为 `函数引用 Map`，
 * 宿主按 `pluginId + 函数名` 直接查表调用，**无需**再进入 Lua 环境做符号查找，
 * 满足高频直调场景（尤其 dedicated 运行时插件）。
 *
 * 两级索引：
 * - `byPlugin`：pluginId → (函数名 → LuaFunction)，宿主显式调用；
 * - `byEvent`：事件名 → 处理函数列表，事件调度器快速派发。
 */
class ExportedFunctionMap {

    private val byPlugin: MutableMap<String, MutableMap<String, LuaFunction>> =
        ConcurrentHashMap()

    private val byEvent: MutableMap<String, MutableList<LuaFunction>> =
        ConcurrentHashMap()

    /** 注册一个导出函数（同时按函数名建立事件索引）。 */
    fun register(pluginId: String, name: String, function: LuaFunction) {
        byPlugin.computeIfAbsent(pluginId) { ConcurrentHashMap() }[name] = function
        byEvent.computeIfAbsent(name) { CopyOnWriteArrayList() }.add(function)
    }

    /** 批量注册一个导出表（插件入口 return 的 LuaTable）。 */
    fun registerAll(pluginId: String, exports: LuaTable) {
        for (key in exports.keys()) {
            val fn = exports.get(key)
            if (fn.isfunction()) {
                register(pluginId, key.tojstring(), fn.checkfunction())
            }
        }
    }

    /** 注销某插件的全部导出函数。 */
    fun unregisterPlugin(pluginId: String) {
        val functions = byPlugin.remove(pluginId) ?: return
        for ((name, fn) in functions) {
            byEvent[name]?.remove(fn)
        }
    }

    /** 按插件 + 函数名查找。 */
    fun find(pluginId: String, name: String): LuaFunction? =
        byPlugin[pluginId]?.get(name)

    /** 某插件导出的全部函数名。 */
    fun names(pluginId: String): Set<String> =
        byPlugin[pluginId]?.keys?.toSet() ?: emptySet()

    /** 监听某事件的全部处理函数（事件驱动派发）。 */
    fun handlersFor(eventName: String): List<LuaFunction> =
        byEvent[eventName] ?: emptyList()

    /** 宿主高频直调：查表 + 调用，一步完成。 */
    fun invoke(pluginId: String, name: String, args: Varargs): Varargs? {
        val function = find(pluginId, name) ?: return null
        return function.invoke(args)
    }

    /** 导出表条目数（诊断用）。 */
    val size: Int get() = byPlugin.values.sumOf { it.size }

    /** 调试辅助：导出 Lua 值到 LuaTable。 */
    fun snapshot(pluginId: String): LuaTable {
        val table = LuaTable()
        byPlugin[pluginId]?.forEach { (name, fn) -> table.set(name, fn) }
        return table
    }

    companion object {
        /** 构造参数便捷函数。 */
        fun varargsOf(vararg values: LuaValue): Varargs = LuaValue.varargsOf(values)
    }
}
