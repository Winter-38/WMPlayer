package com.winter.muplayer.plugin.runtime

import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.CoroutineLib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseIoLib
import org.luaj.vm2.lib.jse.JseMathLib
import org.luaj.vm2.lib.jse.JseOsLib

/**
 * Lua 运行时工厂。
 *
 * 刻意**不使用** `JsePlatform.standardGlobals()`：它携带 LuajavaLib / JSR-223 绑定，
 * 依赖 `java.beans` 与 `javax.script`，在 Android 上不可用。这里只装配 Lua 5.2
 * 标准库（base/package/table/string/math/coroutine/bit32/io/os），Android 全兼容，
 * 且每个 [Globals] 都是相互隔离的独立环境——这正是 x+1 多运行时模型的基础。
 *
 * 宿主 API（如 `wm.log`）通过 [HostFunction] 显式注入，不依赖反射桥（luajava）。
 */
object LuaRuntimeFactory {

    /** 创建一个全新的、隔离的 Lua 全局环境。 */
    fun newGlobals(): Globals {
        val globals = Globals()
        // LuaJ 自建 Globals 时 compiler/loader/undumper 默认为 null，必须显式注入
        globals.compiler = LuaC.instance
        globals.loader = LuaC.instance
        globals.undumper = LoadState.instance
        globals.load(JseBaseLib())
        globals.load(PackageLib())
        globals.load(Bit32Lib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(CoroutineLib())
        globals.load(JseMathLib())
        globals.load(JseIoLib())
        globals.load(JseOsLib())
        return globals
    }
}
