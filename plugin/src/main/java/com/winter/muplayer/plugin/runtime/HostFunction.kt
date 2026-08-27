package com.winter.muplayer.plugin.runtime

import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs

/**
 * 宿主 API 桥：把宿主侧 Kotlin lambda 包装成 Lua 可调用的函数。
 *
 * 这是宿主 ↔ Lua 的唯一通道，替代 luajava 的反射桥（后者在 Android 上不可用）。
 * 通过 [HostFunction] 注入的 API（例如 `wm.log`）在插件内表现为普通 Lua 函数。
 *
 * **LuaJ 调用约定（关键）**：Lua 字节码的 CALL 指令走 `call()` 系列方法
 * （`call()` / `call(arg)` / `call(a,b)` / `call(a,b,c)`），而 `LuaValue` 的
 * 默认实现是 `callmt()`（metatable `__call` 查找），对普通函数会抛
 * "attempt to call function"。因此**必须全部重写并委托到 [invoke]**，
 * 仅重写 `invoke(Varargs)` 不足以让 Lua 侧调用成功（其余 `invoke` 变体
 * 默认委托到 `invoke(Varargs)`，无需逐个覆盖）。
 */
class HostFunction(private val impl: (Varargs) -> Varargs) : LuaFunction() {

    override fun invoke(varargs: Varargs): Varargs = impl(varargs)

    override fun call(): LuaValue = impl(LuaValue.NONE).arg1()

    override fun call(arg: LuaValue): LuaValue =
        impl(LuaValue.varargsOf(arrayOf(arg))).arg1()

    override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue =
        impl(LuaValue.varargsOf(arrayOf(arg1, arg2))).arg1()

    override fun call(arg1: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue =
        impl(LuaValue.varargsOf(arrayOf(arg1, arg2, arg3))).arg1()
}
