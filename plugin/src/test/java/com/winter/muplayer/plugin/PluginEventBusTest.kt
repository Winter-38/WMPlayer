package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.bridge.PluginEventBus
import com.winter.muplayer.plugin.runtime.HostFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.Collections

/**
 * 验证事件总线（eventBus.on / emit）与播放器事件桥接分发：
 * 订阅回调、外部事件 → 生命周期回调映射（trackChanged → onTrackChanged）。
 */
class PluginEventBusTest {

    @Test
    fun `on 订阅后 emit 触发回调`() {
        val bus = PluginEventBus()
        val received = Collections.synchronizedList(mutableListOf<String>())
        bus.on("onTick", HostFunction { args ->
            received.add("${args.arg(1).tojstring()}:${args.arg(2).tojstring()}")
            LuaValue.NONE
        })

        bus.emit("onTick", LuaValue.valueOf("hello"))
        assertEquals(listOf("onTick:hello"), received)
    }

    @Test
    fun `外部播放器事件分发到订阅与生命周期回调`() {
        val bus = PluginEventBus()
        val calls = Collections.synchronizedList(mutableListOf<String>())

        // eventBus.on("trackChanged", cb)
        bus.on("trackChanged", HostFunction { args ->
            calls.add("subscription:${args.arg(2).get("title").tojstring()}")
            LuaValue.NONE
        })

        // 模拟插件全局环境：onTrackChanged 生命周期回调
        val globals = LuaTable()
        globals.set("onTrackChanged", HostFunction { args ->
            calls.add("lifecycle:${args.arg(1).get("title").tojstring()}")
            LuaValue.NONE
        })

        val data = LuaTable().also { it.set("title", LuaValue.valueOf("Song")) }
        bus.dispatchExternal("trackChanged", data, globals)

        assertEquals(2, calls.size)
        assertTrue(calls[0].startsWith("subscription:"))
        assertTrue(calls[1] == "lifecycle:Song")
    }

    @Test
    fun `外部事件无对应生命周期回调时安全跳过`() {
        val bus = PluginEventBus()
        val globals = LuaTable()
        // 未实现 onProgressUpdated
        bus.dispatchExternal("progressUpdated", LuaValue.valueOf(1.0), globals)
        assertTrue(true) // 不抛异常即通过
    }

    @Test
    fun `clear 清理全部订阅`() {
        val bus = PluginEventBus()
        bus.on("x", HostFunction { LuaValue.NONE })
        assertTrue(bus.hasListener("x"))
        bus.clear()
        assertTrue(!bus.hasListener("x"))
    }
}
