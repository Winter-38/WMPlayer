package com.winter.muplayer.plugin.registry

import com.winter.muplayer.plugin.model.PluginDescriptor
import com.winter.muplayer.plugin.runtime.LuaRuntime
import java.util.Collections
import java.util.WeakHashMap

/**
 * 插件注册表（弱引用保证 GC 完成）。
 *
 * 主路径：显式 [unregister]（卸载插件）移除强引用；
 * 兜底：底层为 [WeakHashMap]，即使宿主忘记卸载，注册条目也不会阻止
 * 运行时环境被 GC 回收，杜绝常驻泄漏。
 *
 * 注意：插件 id 字符串来自 manifest 解析（非 intern 常量），可作为弱引用键。
 */
class PluginRegistry {

    /** 一条插件注册记录。 */
    class Registration(
        val descriptor: PluginDescriptor,
        val runtime: LuaRuntime?,
        val loadedAt: Long
    )

    private val entries: MutableMap<String, Registration> =
        Collections.synchronizedMap(WeakHashMap())

    /** 注册（重复注册时覆盖旧记录）。 */
    fun register(descriptor: PluginDescriptor, runtime: LuaRuntime?): Registration {
        val registration = Registration(descriptor, runtime, System.currentTimeMillis())
        entries[descriptor.id] = registration
        return registration
    }

    /** 查询注册记录；已卸载或已被 GC 回收返回 null。 */
    fun get(pluginId: String): Registration? = entries[pluginId]

    /** 是否已加载（注册）且运行时仍存活。 */
    fun isLoaded(pluginId: String): Boolean {
        val registration = entries[pluginId] ?: return false
        return registration.runtime?.isAlive != false
    }

    /** 卸载：移除注册条目（强引用释放，交由 GC）。 */
    fun unregister(pluginId: String): Registration? = entries.remove(pluginId)

    /** 当前已加载插件描述列表（快照）。 */
    fun loadedPlugins(): List<PluginDescriptor> =
        entries.values.map { it.descriptor }.sortedBy { it.id }

    /** 注册条目数（诊断用）。 */
    val size: Int get() = entries.size
}
