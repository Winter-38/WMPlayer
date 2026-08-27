package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.model.PluginDescriptor
import com.winter.muplayer.plugin.runtime.RuntimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/**
 * 验证 x+1 运行时模型：
 * x 个 dedicated 插件各自独立常驻运行时；所有 shared 插件共享同一运行时。
 */
class RuntimeManagerTest {

    private fun descriptor(id: String, runtime: RuntimeKind) = PluginDescriptor(
        id = id,
        name = id,
        version = "1.0.0",
        entry = "main.lua",
        runtime = runtime,
        events = emptyList(),
        dir = File("/tmp/plugins/$id")
    )

    @Test
    fun `shared 插件共享同一个运行时`() {
        val manager = RuntimeManager()
        val a = manager.runtimeFor(descriptor("a", RuntimeKind.SHARED))
        val b = manager.runtimeFor(descriptor("b", RuntimeKind.SHARED))
        assertSame("共享运行时应唯一", a, b)
        assertSame(a, manager.sharedRuntime)
    }

    @Test
    fun `dedicated 插件各自独占运行时且常驻`() {
        val manager = RuntimeManager()
        val a = manager.runtimeFor(descriptor("hot-a", RuntimeKind.DEDICATED))
        val b = manager.runtimeFor(descriptor("hot-b", RuntimeKind.DEDICATED))
        assertNotSame("高频插件运行时必须隔离", a, b)

        // 重复获取同一插件的运行时：常驻复用
        val a2 = manager.runtimeFor(descriptor("hot-a", RuntimeKind.DEDICATED))
        assertSame(a, a2)
        assertEquals(2, manager.dedicatedCount)

        // 释放后不再常驻
        manager.release("hot-a")
        assertEquals(1, manager.dedicatedCount)
        val a3 = manager.runtimeFor(descriptor("hot-a", RuntimeKind.DEDICATED))
        assertNotSame("释放后应重建", a, a3)
    }

    @Test
    fun `运行时环境完全隔离`() {
        val manager = RuntimeManager()
        val shared = manager.sharedRuntime
        val dedicated = manager.runtimeFor(descriptor("hot", RuntimeKind.DEDICATED))

        shared.set("_isolated", org.luaj.vm2.LuaValue.valueOf("shared"))
        val dedicatedGlobals = dedicated.weakGlobals.get()!!
        // dedicated 运行时不应看到 shared 里注入的变量（环境隔离）
        assertEquals(org.luaj.vm2.LuaValue.NIL, dedicatedGlobals.get("_isolated"))
        // shared 运行时自身能看到
        assertEquals("shared", shared.weakGlobals.get()!!.get("_isolated").tojstring())
    }
}
