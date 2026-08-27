package com.winter.muplayer.plugin.runtime

import com.winter.muplayer.plugin.registry.ExportedFunctionMap
import com.winter.muplayer.plugin.model.PluginEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 共享运行时的事件调度器（事件驱动插件隔离执行）。
 *
 * 设计要点：
 * - **串行化**：所有事件进入无界 [Channel]，由单个消费者协程逐个派发，天然保证
 *   共享 [LuaRuntime]（非线程安全）不会被并发进入；
 * - **协程隔离**：宿主侧以协程（Channel 消费者）承载调度；插件侧可通过
 *   [LuaRuntime.createCoroutine] 在 Lua 协程栈内 yield/resume，实现插件间时间片隔离；
 * - **故障隔离**：单个插件的异常被捕获并记录，不影响后续事件与其它插件。
 */
class SharedEventExecutor(
    private val runtime: LuaRuntime,
    private val exported: ExportedFunctionMap,
    scope: CoroutineScope
) {
    private val channel = Channel<PluginEvent>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in channel) {
                dispatch(event)
            }
        }
    }

    /** 投递事件（非阻塞）。 */
    fun emit(event: PluginEvent) {
        channel.trySend(event)
    }

    private fun dispatch(event: PluginEvent) {
        val args = LuaConversions.toVarargs(event)
        // 按事件名查导出函数表，命中该事件的所有插件处理函数
        for (handler in exported.handlersFor(event.name)) {
            try {
                handler.invoke(args)
            } catch (t: Throwable) {
                // 插件异常隔离：记录并继续分发，不拖垮共享运行时
                android.util.Log.w("LuaPlugin", "事件 ${event.name} 处理失败: ${t.message}", t)
            }
        }
    }
}
