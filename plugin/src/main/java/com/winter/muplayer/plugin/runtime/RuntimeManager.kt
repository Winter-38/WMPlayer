package com.winter.muplayer.plugin.runtime

import com.winter.muplayer.plugin.RuntimeKind
import com.winter.muplayer.plugin.model.PluginDescriptor
import org.luaj.vm2.Globals

/**
 * x+1 运行时管理器：
 *
 * - **x**：每个 [RuntimeKind.DEDICATED]（高频调用）插件独占一个常驻 [LuaRuntime]，
 *   互不阻塞、可被宿主高频直调；
 * - **1**：所有 [RuntimeKind.SHARED]（事件驱动）插件共享同一个 [LuaRuntime]，
 *   由 SharedEventExecutor 按协程串行调度。
 *
 * 常驻运行时在 [release] 前保持强引用；[release] 移除强引用后，配合
 * [LuaRuntime.weakGlobals] 由 GC 完成回收（弱引用兜底，不泄漏）。
 */
class RuntimeManager {

    private val shared: LuaRuntime = LuaRuntime(LuaRuntimeFactory.newGlobals())

    private val dedicatedRuntimes: MutableMap<String, LuaRuntime> = HashMap()

    /** 共享运行时（事件驱动插件共用）。 */
    val sharedRuntime: LuaRuntime get() = shared

    /** 按插件描述选择运行时：dedicated 走专用常驻，shared 走共享。 */
    fun runtimeFor(descriptor: PluginDescriptor): LuaRuntime =
        when (descriptor.runtime) {
            RuntimeKind.DEDICATED -> dedicatedFor(descriptor.id)
            RuntimeKind.SHARED -> shared
        }

    /** 取（或创建）某个插件的专用常驻运行时。 */
    fun dedicatedFor(pluginId: String): LuaRuntime =
        dedicatedRuntimes.getOrPut(pluginId) { LuaRuntime(LuaRuntimeFactory.newGlobals()) }

    /** 释放专用运行时（移除强引用，交还 GC），共享运行时不受影响。 */
    fun release(pluginId: String) {
        dedicatedRuntimes.remove(pluginId)
    }

    /** 释放全部专用运行时。 */
    fun releaseAll() {
        dedicatedRuntimes.clear()
    }

    /** 当前存活的专用运行时数量（x 的值）。 */
    val dedicatedCount: Int get() = dedicatedRuntimes.size

    /** 供测试/诊断使用。 */
    internal fun rawSharedGlobals(): Globals = shared.weakGlobals.get()!!
}
