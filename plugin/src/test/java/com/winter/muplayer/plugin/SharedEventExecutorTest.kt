package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.model.PluginEvent
import com.winter.muplayer.plugin.registry.ExportedFunctionMap
import com.winter.muplayer.plugin.runtime.HostFunction
import com.winter.muplayer.plugin.runtime.LuaRuntime
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import com.winter.muplayer.plugin.runtime.SharedEventExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.luaj.vm2.LuaValue
import java.util.Collections

/**
 * 验证事件驱动调度：共享运行时内按事件名串行派发到各插件导出函数。
 */
class SharedEventExecutorTest {

    @Test
    fun `事件按序串行派发到所有监听插件`() = runBlocking {
        val exported = ExportedFunctionMap()
        val runtime = LuaRuntime(LuaRuntimeFactory.newGlobals())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executor = SharedEventExecutor(runtime, exported, scope)

        val received = Collections.synchronizedList(mutableListOf<String>())
        val handlerA = HostFunction { args ->
            received.add("A:${args.arg(2).get("n").toint()}")
            LuaValue.NONE
        }
        val handlerB = HostFunction { args ->
            received.add("B:${args.arg(2).get("n").toint()}")
            LuaValue.NONE
        }
        exported.register("pA", "onTick", handlerA)
        exported.register("pB", "onTick", handlerB)

        executor.emit(PluginEvent("onTick", mapOf("n" to 1)))
        executor.emit(PluginEvent("onTick", mapOf("n" to 2)))

        delay(200)

        assertEquals(
            listOf("A:1", "B:1", "A:2", "B:2"),
            received
        )
        scope.cancel()
    }

    @Test
    fun `无监听事件安全跳过`() = runBlocking {
        val exported = ExportedFunctionMap()
        val runtime = LuaRuntime(LuaRuntimeFactory.newGlobals())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val executor = SharedEventExecutor(runtime, exported, scope)

        executor.emit(PluginEvent("nothingListens"))
        delay(100)
        // 不抛异常即通过
        scope.cancel()
    }
}
