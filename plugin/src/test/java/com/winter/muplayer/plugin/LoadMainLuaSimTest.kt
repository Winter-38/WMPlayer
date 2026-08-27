package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.runtime.HostFunction
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.File

/**
 * 模拟宿主加载流程：注入模拟 wm 表 → 执行真实 main.lua 入口 → 调用 onLoad。
 * 验证 onLoad 的 registerUI 是否真的注册了 15 个组件（12 色块 + 3 结果）。
 *
 * 若此测试失败，说明 main.lua 逻辑在无 Android 依赖下即出错；
 * 若通过，问题在真机 LuaApiBuilder 的某个宿主 API。
 */
class LoadMainLuaSimTest {

    private val mainLua = File("/home/winter/WMPlayer/lua-plugin/theme-generator/main.lua").readText()

    /** 构造模拟 wm 表：覆盖 main.lua 用到的全部宿主 API。 */
    private fun buildMockWm(): Pair<LuaTable, MutableList<String>> {
        val wm = LuaTable()
        val registered = mutableListOf<String>()

        // wm.ui
        val ui = LuaTable()
        ui.set("registerComponent", HostFunction { a ->
            val id = a.arg(1).tojstring()
            registered += id
            LuaValue.TRUE
        })
        ui.set("unregister", HostFunction { a ->
            registered.remove(a.arg(1).tojstring())
            LuaValue.TRUE
        })
        wm.set("ui", ui)

        // wm.log
        val log = LuaTable()
        for (lv in listOf("d", "i", "w", "e")) {
            log.set(lv, HostFunction { a ->
                println("[mock-log.$lv] ${a.arg(1).tojstring()}: ${a.arg(2).tojstring()}")
                LuaValue.NONE
            })
        }
        wm.set("log", log)

        // wm.plugin
        val plugin = LuaTable()
        plugin.set("getConfig", HostFunction { LuaValue.NIL })
        plugin.set("setConfig", HostFunction { LuaValue.TRUE })
        wm.set("plugin", plugin)

        // wm.eventBus
        val bus = LuaTable()
        bus.set("emit", HostFunction { LuaValue.NONE })
        wm.set("eventBus", bus)

        return wm to registered
    }

    @Test
    fun onLoad注册了全部组件() {
        val globals = LuaRuntimeFactory.newGlobals()
        val (wm, registered) = buildMockWm()
        globals.set("wm", wm)

        // 入口执行（源码直跑；字节码路径行为已由 BytecodeUpvalueClosureTest 对齐）
        globals.load(mainLua, "main.lua").call()

        // onLoad → registerUI
        val onLoad = globals.get("onLoad")
        assertTrue("onLoad 应存在", onLoad.isfunction())
        val ok = try {
            onLoad.invoke()
            true
        } catch (t: Throwable) {
            println("onLoad 抛错: ${t.message}")
            t.printStackTrace()
            false
        }
        assertTrue("onLoad 不应抛错", ok)

        // 12 色块 + 3 结果 = 15 个组件
        assertEquals("应注册 15 个组件", 15, registered.size)
        assertEquals(
            (1..12).map { "mt-swatch-$it" } + listOf(
                "mt-result-primary", "mt-result-secondary", "mt-result-tertiary",
            ),
            registered,
        )
    }

    @Test
    fun 导出函数可用() {
        val globals = LuaRuntimeFactory.newGlobals()
        val (wm, _) = buildMockWm()
        globals.set("wm", wm)

        // 入口 return exports 应可通过入口返回值取到
        val result = globals.load(mainLua, "main.lua").call()
        val exports = result.arg1().checktable()
        assertTrue("exports.generatePalette", exports.get("generatePalette").isfunction())
        assertTrue("exports.setPrimaryColor", exports.get("setPrimaryColor").isfunction())
    }
}
