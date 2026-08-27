package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.runtime.LuaRuntime
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaValue

/**
 * 验证事件驱动插件的「协程隔离」执行：
 * 同一共享运行时内，多个插件的 Lua 协程各自挂起/恢复，互不干扰。
 */
class CoroutineIsolationTest {

    @Test
    fun `共享运行时内多个协程轮流 resume 互不干扰`() {
        val runtime = LuaRuntime(LuaRuntimeFactory.newGlobals())

        // 插件 A 的协程：两次 yield 后求和返回
        val coA = runtime.executeSource(
            """
            return coroutine.create(function()
                local a = coroutine.yield("A:first")
                local b = coroutine.yield("A:second")
                return a + b
            end)
            """.trimIndent(),
            "plugin-a.lua"
        ).arg1()

        // 插件 B 的协程：一次 yield 后翻倍返回
        val coB = runtime.executeSource(
            """
            return coroutine.create(function()
                local v = coroutine.yield("B:first")
                return v * 2
            end)
            """.trimIndent(),
            "plugin-b.lua"
        ).arg1()

        // 交替调度（事件驱动隔离的核心：宿主串行 resume）
        // 注意：yield 只返回 resume 传入的第一个值
        var r = runtime.resumeCoroutine(coA)
        assertTrue(r.arg(1).toboolean())
        assertEquals("A:first", r.arg(2).tojstring())

        r = runtime.resumeCoroutine(coB)
        assertTrue(r.arg(1).toboolean())
        assertEquals("B:first", r.arg(2).tojstring())

        r = runtime.resumeCoroutine(coA, LuaValue.valueOf(10))
        assertTrue(r.arg(1).toboolean())
        assertEquals("A:second", r.arg(2).tojstring())

        r = runtime.resumeCoroutine(coB, LuaValue.valueOf(21))
        assertTrue(r.arg(1).toboolean())
        assertEquals("B 完成", 42, r.arg(2).toint())

        r = runtime.resumeCoroutine(coA, LuaValue.valueOf(20))
        assertTrue(r.arg(1).toboolean())
        assertEquals("A 完成", 30, r.arg(2).toint())
    }

    @Test
    fun `插件协程内 yield 让出执行权，不阻塞共享运行时`() {
        val runtime = LuaRuntime(LuaRuntimeFactory.newGlobals())
        val co = runtime.executeSource(
            """
            return coroutine.create(function()
                local sum = 0
                for i = 1, 3 do
                    sum = sum + coroutine.yield(i)
                end
                return sum
            end)
            """.trimIndent(),
            "counter.lua"
        ).arg1()

        var expectedStep = 1
        var r = runtime.resumeCoroutine(co)
        // resume 返回值无法区分 yield 与 return，需用 coroutine.status 判断是否结束
        while (runtime.isCoroutineAlive(co) && r.arg(1).toboolean()) {
            assertEquals(expectedStep, r.arg(2).toint())
            r = runtime.resumeCoroutine(co, LuaValue.valueOf(expectedStep))
            expectedStep++
        }
        // 循环结束后应为最终结果 1+2+3
        assertEquals("协程应已结束", 6, r.arg(2).toint())
    }
}
