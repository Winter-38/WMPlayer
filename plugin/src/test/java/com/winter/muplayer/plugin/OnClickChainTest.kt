package com.winter.muplayer.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

/**
 * 复现 theme-generator 色块 onClick 的完整执行链路（Lua 侧）：
 * 加载 main.lua → onLoad 注册组件（解析 onClick 为 LuaFunction）→
 * 模拟点击 invoke onClick → setConfig 同步触发 onConfigChanged →
 * onConfigChanged 里 updateResultComponents。
 *
 * 若此测试通过，说明 Lua 侧完整链路无误，bug 在宿主 Kotlin 侧（手势绑定/调度）。
 */
class OnClickChainTest {

    private val mainLua = File("/home/winter/WMPlayer/lua-plugin/theme-generator/main.lua").readText()

    @Test
    fun onClick完整链路_updateResultComponents正常() {
        val globals = JsePlatform.standardGlobals()
        val configStore = HashMap<String, LuaValue>()
        val onClickFns = HashMap<String, LuaValue>()
        val registered = ArrayList<String>()

        // wm.log
        val log = LuaTable()
        for (lv in listOf("d", "i", "w", "e")) {
            log.set(lv, object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs = LuaValue.NONE
            })
        }

        // wm.plugin：setConfig 同步触发 onConfigChanged（复刻 LuaApiBuilder 行为）
        val plugin = LuaTable()
        plugin.set("getConfig", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue =
                configStore[arg.tojstring()] ?: LuaValue.NIL
        })
        plugin.set("setConfig", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val key = args.arg(1).tojstring()
                val newVal = args.arg(2)
                val oldVal = configStore[key] ?: LuaValue.NIL
                configStore[key] = newVal
                val fn = globals.get("onConfigChanged")
                if (fn.isfunction()) {
                    fn.invoke(LuaValue.valueOf(key), newVal, oldVal)
                }
                return LuaValue.TRUE
            }
        })

        // wm.ui：registerComponent 解析 onClick 为 LuaFunction（复刻 resolveAction）
        val ui = LuaTable()
        ui.set("registerComponent", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val id = args.arg(1).tojstring()
                val cfg = args.arg(2).checktable()
                registered.add(id)
                val onClick = cfg.get("onClick")
                if (onClick.isfunction()) onClickFns[id] = onClick
                return LuaValue.TRUE
            }
        })
        ui.set("unregister", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                registered.remove(arg.tojstring())
                return LuaValue.TRUE
            }
        })

        // wm.eventBus
        val bus = LuaTable()
        bus.set("emit", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = LuaValue.NONE
        })

        val wm = LuaTable()
        wm.set("log", log)
        wm.set("plugin", plugin)
        wm.set("ui", ui)
        wm.set("eventBus", bus)
        globals.set("wm", wm)

        // 入口执行 + onLoad
        globals.load(mainLua, "main.lua").call()
        globals.get("onLoad").invoke()

        assertEquals("应注册 15 个组件", 15, registered.size)
        assertEquals("应解析 12 个色块 onClick", 12, onClickFns.size)

        // 模拟点击色块 1
        val onClick = onClickFns["mt-swatch-1"]
        assertTrue("色块 1 应有 onClick", onClick != null)
        onClick!!.invoke()

        // 主色应更新为 #F44336（PRESET_COLORS[1]）
        assertEquals("#F44336", configStore["primaryColor"]?.tojstring())

        // 结果组件应被重新注册（updateResultComponents 执行了 unregister + register）
        assertTrue("结果组件应注册", registered.contains("mt-result-primary"))
    }
}
