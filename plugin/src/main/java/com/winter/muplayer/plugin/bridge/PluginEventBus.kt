package com.winter.muplayer.plugin.bridge

import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件事件总线（`eventBus.on` / `eventBus.emit`）。
 *
 * - [on]：订阅事件（含播放器预定义事件 trackChanged / playStateChanged /
 *   progressUpdated），回调为 Lua 函数，统一接收 `(event, data)`；
 * - [emit]：触发本插件已订阅的事件；
 * - [dispatchExternal]：由全局播放器事件桥接调用，触发订阅 + 同名生命周期回调
 *   （onTrackChanged 等）。
 *
 * 每个插件一个实例，回调持有插件运行时的函数引用，插件卸载时随 [clear] 释放。
 */
class PluginEventBus {

    private val listeners: MutableMap<String, MutableList<LuaFunction>> =
        ConcurrentHashMap()

    /** 订阅事件，返回取消订阅函数（Lua 值）。 */
    fun on(event: String, callback: LuaFunction): LuaValue {
        listeners.computeIfAbsent(event) { Collections.synchronizedList(mutableListOf()) }
            .add(callback)
        return LuaValue.valueOf(true)
    }

    /** 触发本插件订阅的事件。 */
    fun emit(event: String, data: LuaValue) {
        fireListeners(event, data)
    }

    /**
     * 外部事件分发（播放器桥接）：触发订阅，并调用与该事件对应的生命周期回调
     * （如 trackChanged → onTrackChanged），回调统一接收 `data`。
     */
    fun dispatchExternal(event: String, data: LuaValue, globals: LuaValue?) {
        fireListeners(event, data)
        // 生命周期回调：event → onEvent（首字母大写）
        val lifecycle = "on" + event.replaceFirstChar { it.uppercase() }
        globals?.get(lifecycle)?.takeIf { it.isfunction() }?.invoke(data)
    }

    /** 查询某事件是否有订阅。 */
    fun hasListener(event: String): Boolean =
        listeners[event]?.isNotEmpty() == true

    /** 清理该插件全部订阅（卸载时调用）。 */
    fun clear() {
        listeners.clear()
    }

    private fun fireListeners(event: String, data: LuaValue) {
        val list = listeners[event] ?: return
        // 快照遍历，允许回调中增删订阅
        for (fn in list.toList()) {
            try {
                fn.invoke(LuaValue.valueOf(event), data)
            } catch (t: Throwable) {
                android.util.Log.w("LuaPlugin", "事件 $event 回调失败: ${t.message}", t)
            }
        }
    }
}
