package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.bridge.PluginScheduler
import com.winter.muplayer.plugin.runtime.HostFunction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaValue
import java.util.Collections

/**
 * 验证插件调度器（thread / timer）：setTimeout / setInterval / clear。
 */
class PluginSchedulerTest {

    @Test
    fun `setTimeout 延迟执行一次`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler = PluginScheduler(scope)
        val calls = Collections.synchronizedList(mutableListOf<String>())

        val id = scheduler.setTimeout(30, HostFunction {
            calls.add("tick")
            LuaValue.NONE
        })

        delay(100)
        assertEquals(listOf("tick"), calls)
        scheduler.clear(id)
        scope.cancel()
    }

    @Test
    fun `setInterval 周期性执行且 clear 停止`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler = PluginScheduler(scope)
        val count = java.util.concurrent.atomic.AtomicInteger(0)

        val id = scheduler.setInterval(20, HostFunction {
            count.incrementAndGet()
            LuaValue.NONE
        })

        delay(100)   // 期望约 5 次
        scheduler.clear(id)
        val before = count.get()
        delay(80)
        assertEquals("clear 后不再执行", before, count.get())
        assertTrue("至少触发了一次 (actual=$before)", before >= 2)
        scope.cancel()
    }

    @Test
    fun `clearAll 清理全部定时器`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler = PluginScheduler(scope)
        val count = java.util.concurrent.atomic.AtomicInteger(0)

        scheduler.setInterval(20, HostFunction { count.incrementAndGet(); LuaValue.NONE })
        scheduler.setInterval(20, HostFunction { count.incrementAndGet(); LuaValue.NONE })
        scheduler.setTimeout(20, HostFunction { LuaValue.NONE })

        scheduler.clearAll()
        val before = count.get()
        delay(80)
        assertEquals(before, count.get())
        scope.cancel()
    }
}
