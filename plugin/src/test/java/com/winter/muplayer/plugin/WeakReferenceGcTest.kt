package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.registry.PluginRegistry
import com.winter.muplayer.plugin.runtime.LuaRuntime
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference

/**
 * 验证「弱引用确保 GC 完成」：
 * 卸载（移除强引用）后，运行时环境与注册条目可被 GC 回收，不泄漏。
 */
class WeakReferenceGcTest {

    private fun forceGc() {
        repeat(20) {
            System.gc()
            Thread.sleep(10)
            // 分配垃圾，促使 GC 真正执行
            @Suppress("UNUSED_VARIABLE")
            val junk = ByteArray(512 * 1024)
        }
    }

    @Test
    fun `运行时移除强引用后 Globals 被 GC 回收`() {
        var runtime: LuaRuntime? = LuaRuntime(LuaRuntimeFactory.newGlobals())
        val weakGlobals: WeakReference<*> = runtime!!.weakGlobals
        assertTrue(runtime!!.isAlive)

        // 模拟卸载：释放唯一强引用
        runtime = null

        forceGc()
        assertNull("GC 应回收无引用的 Globals", weakGlobals.get())
    }

    @Test
    fun `插件注册条目卸载后不再阻挡 GC`() {
        val registry = PluginRegistry()
        var runtime: LuaRuntime? = LuaRuntime(LuaRuntimeFactory.newGlobals())
        val weak: WeakReference<*> = runtime!!.weakGlobals

        val descriptor = com.winter.muplayer.plugin.model.PluginDescriptor(
            id = "gc.test",
            name = "GcTest",
            version = "1.0.0",
            entry = "main.lua",
            runtime = RuntimeKind.SHARED,
            events = emptyList(),
            dir = File("/tmp/plugins/gc.test")
        )
        registry.register(descriptor, runtime)
        assertTrue(registry.isLoaded("gc.test"))

        // 卸载：unregister 移除注册条目
        registry.unregister("gc.test")
        runtime = null

        forceGc()
        assertNull("卸载后运行时应可被回收", weak.get())
        assertEquals(0, registry.size)
    }
}
