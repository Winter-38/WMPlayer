package com.winter.muplayer.plugin.runtime

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import java.io.InputStream
import java.lang.ref.WeakReference

/**
 * 一个 Lua 运行时封装：持有独立 [Globals] 环境。
 *
 * - **常驻语义**：调用方（RuntimeManager）持有本对象强引用期间，运行时不被回收；
 * - **弱引用兜底**：[weakGlobals] 用于检测 GC 是否已完成回收（配合 `unload` 移除强引用后，
 *   GC 可回收整个环境，避免常驻运行时泄漏）。
 *
 * 所有 Lua 执行都是同步的，宿主侧通过协程调度器保证同一运行时不并发进入。
 */
class LuaRuntime internal constructor(globals: Globals) {

    private val globals: Globals = globals

    /** 弱引用副本：`unload` 后用于确认 GC 已完成回收。 */
    val weakGlobals: WeakReference<Globals> = WeakReference(globals)

    /** 运行时是否仍存活（未被 GC 回收）。 */
    val isAlive: Boolean get() = weakGlobals.get() != null

    /** 在全局环境作用域内安全执行一段同步代码（失败返回 null）。 */
    fun <T> withGlobals(block: (Globals) -> T): T? = weakGlobals.get()?.let(block)

    /** 在全局环境中挂一个 Lua 值（注入宿主 API 等）。 */
    fun set(name: String, value: LuaValue) {
        globals.set(name, value)
    }

    /** 注入宿主 API 表，例如 `wm`。 */
    fun inject(name: String, table: LuaTable) {
        globals.set(name, table)
    }

    /** 执行 Lua 源码（入口脚本路径：source 加载 + call）。 */
    fun executeSource(source: String, chunkName: String): Varargs =
        globals.load(source, chunkName).call()

    /** 执行 Lua 字节码（二进制 chunk，Undumper 自动识别）。 */
    fun executeBytecode(input: InputStream, chunkName: String): Varargs =
        globals.load(input, chunkName, "bt", globals).call()

    /**
     * 加载 Lua 值（源码/字节码）但不调用，返回闭包。
     * 宿主可将其用于 `coroutine.create` 等场景。
     */
    fun loadChunk(input: InputStream, chunkName: String): LuaValue =
        globals.load(input, chunkName, "bt", globals)

    /** 获取导出表（插件入口 return 的表），未导出返回 null。 */
    fun exportedTable(): LuaTable? = globals.get("_exports").opttable(null)

    /**
     * 创建 Lua 协程（基于 coroutine.create），用于事件驱动插件内部的时间片隔离：
     * 多个插件在同一共享运行时内各自拥有独立协程栈，`yield` 挂起、宿主 `resume` 唤醒。
     */
    fun createCoroutine(function: LuaValue): LuaValue {
        val coroutineLib = globals.get("coroutine")
        return coroutineLib.get("create").invoke(function).arg1()
    }

    /** 恢复一个挂起的 Lua 协程，返回 resume 的结果（ok 标志 + 值）。 */
    fun resumeCoroutine(co: LuaValue, vararg args: LuaValue): Varargs {
        val coroutineLib = globals.get("coroutine")
        return coroutineLib.get("resume").invoke(LuaValue.varargsOf(arrayOf(co) + args))
    }

    /** 协程是否仍在运行（suspended/running）；dead 表示已结束。 */
    fun isCoroutineAlive(co: LuaValue): Boolean {
        val coroutineLib = globals.get("coroutine")
        return coroutineLib.get("status").invoke(co).tojstring() != "dead"
    }
}
