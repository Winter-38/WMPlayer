package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.runtime.HostFunction
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/** 最小复现：Lua 侧以 table 方法形式调用 HostFunction（wm.ui.registerComponent(id, cfg)）。 */
class HostFunctionCallMinimalTest {

    @Test
    fun luaCallsHostFunctionWithTableArg() {
        val globals = LuaRuntimeFactory.newGlobals()
        val ui = LuaTable()
        val called = mutableListOf<String>()
        ui.set("registerComponent", HostFunction { a ->
            called += a.arg(1).tojstring()
            LuaValue.TRUE
        })
        globals.set("wm", LuaTable().also { it.set("ui", ui) })

        val src = """
            wm.ui.registerComponent("mt-swatch-1", {
              title = "#F44336",
              icon = "disc",
              onClick = function() end,
            })
        """.trimIndent()

        val ok = try {
            globals.load(src, "min.lua").call()
            true
        } catch (t: Throwable) {
            println("调用抛错: ${t.message}")
            t.printStackTrace()
            false
        }
        assertTrue("HostFunction 调用不应抛错", ok)
        assertEquals(listOf("mt-swatch-1"), called)
    }
}
