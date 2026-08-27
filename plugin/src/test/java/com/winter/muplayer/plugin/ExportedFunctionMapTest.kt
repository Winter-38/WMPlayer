package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.registry.ExportedFunctionMap
import com.winter.muplayer.plugin.runtime.HostFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaValue

/**
 * 验证「函数引用存入 map 由宿主快速读取」：
 * 注册、事件索引、按插件查表直调、注销清理。
 */
class ExportedFunctionMapTest {

    private fun addFn(): HostFunction = HostFunction { args ->
        LuaValue.valueOf(args.arg(1).toint() + args.arg(2).toint())
    }

    @Test
    fun `宿主查表直调插件导出函数`() {
        val map = ExportedFunctionMap()
        map.register("p1", "add", addFn())

        val result = map.invoke(
            "p1", "add",
            LuaValue.varargsOf(arrayOf(LuaValue.valueOf(2), LuaValue.valueOf(3)))
        )
        assertEquals(5, result!!.arg1().toint())
        assertEquals(setOf("add"), map.names("p1"))
    }

    @Test
    fun `事件索引支持按事件名批量派发`() {
        val map = ExportedFunctionMap()
        val fnA = addFn()
        val fnB = addFn()
        map.register("pA", "onTick", fnA)
        map.register("pB", "onTick", fnB)

        val handlers = map.handlersFor("onTick")
        assertEquals(2, handlers.size)
        assertTrue(handlers.contains(fnA))
        assertTrue(handlers.contains(fnB))
        assertEquals(0, map.handlersFor("other").size)
    }

    @Test
    fun `从 Lua 导出表批量注册`() {
        val map = ExportedFunctionMap()
        val globals = com.winter.muplayer.plugin.runtime.LuaRuntimeFactory.newGlobals()
        val exports = globals.load(
            """
            return { foo = function() return "foo" end, bar = 42 }
            """.trimIndent(),
            "exports.lua"
        ).call().arg1().checktable()

        map.registerAll("p2", exports)

        // 只有函数被注册（bar 是数字，跳过）
        assertEquals(setOf("foo"), map.names("p2"))
        assertEquals("foo", map.find("p2", "foo")!!.invoke().tojstring())
    }

    @Test
    fun `注销插件后索引同步清理`() {
        val map = ExportedFunctionMap()
        val fnA = addFn()
        val fnB = addFn()
        map.register("p1", "add", fnA)
        map.register("p2", "add", fnB)

        assertEquals(2, map.handlersFor("add").size)

        map.unregisterPlugin("p1")

        assertNull(map.find("p1", "add"))
        // p2 的处理函数仍留在事件索引中
        assertEquals(listOf(fnB), map.handlersFor("add"))
        // 重复注销安全
        map.unregisterPlugin("p1")
    }
}
