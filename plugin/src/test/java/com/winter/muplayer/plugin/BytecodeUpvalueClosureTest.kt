package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.bytecode.BytecodeCompiler
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.luaj.vm2.LuaValue
import java.io.ByteArrayInputStream

/**
 * 决定性验证：插件入口经「字节码缓存（.ljbc）」路径执行时，
 * 全局回调函数（onLoad）闭包捕获的 local 是否保留。
 *
 * theme-generator 的 main.lua 结构：local PRESET_COLORS / local registerUI，
 * 全局 function onLoad() 内调用 registerUI()。若字节码 dump/undump 后
 * upvalue 丢失，onLoad 执行会抛错或读到 nil → 组件注册 0 个。
 */
class BytecodeUpvalueClosureTest {

    private val source = """
        -- 模拟 main.lua 结构：local 数据 + local 函数 + 全局 onLoad 闭包
        local PRESET = {"#F44336", "#E91E63", "#9C27B0"}
        local registered = 0

        local function registerUI()
          for i = 1, #PRESET do
            registered = registered + 1
          end
        end

        function onLoad()
          registerUI()
          RESULT = registered
        end

        return {}
    """.trimIndent()

    /** 直接源码执行（对照组：不经过字节码） */
    @Test
    fun sourcePath_upvalue正常() {
        val globals = LuaRuntimeFactory.newGlobals()
        globals.load(source, "test.lua").call()
        globals.get("onLoad").invoke()
        assertEquals("源码路径应注册 3 个", 3, globals.get("RESULT").toint())
    }

    /** 字节码缓存路径：compile → load("bt") → call → onLoad */
    @Test
    fun bytecodePath_upvalue保留() {
        val bytes = BytecodeCompiler.compile(source, "test.lua")
        val globals = LuaRuntimeFactory.newGlobals()
        val chunk = globals.load(ByteArrayInputStream(bytes), "test.ljbc", "bt", globals)
        chunk.call()
        // 入口应能正常返回（此处返回空表）
        globals.get("onLoad").invoke()
        assertEquals("字节码路径应注册 3 个（upvalue 保留）", 3, globals.get("RESULT").toint())
    }

    /** 字节码路径：onLoad 闭包内再访问捕获的 local 表（与色块循环一致） */
    @Test
    fun bytecodePath_闭包内访问local表() {
        val bytes = BytecodeCompiler.compile(source, "test.lua")
        val globals = LuaRuntimeFactory.newGlobals()
        val chunk = globals.load(ByteArrayInputStream(bytes), "test.ljbc", "bt", globals)
        chunk.call()

        // 模拟 registerUI 循环读取 PRESET[i] 构造 table 传给宿主
        val fn = globals.get("onLoad")
        val ok = try {
            fn.invoke()
            true
        } catch (t: Throwable) {
            println("onLoad 抛错: ${t.message}")
            false
        }
        assertEquals("onLoad 不应抛错", true, ok)
        assertEquals("PRESET 表应完整可读", 3, globals.get("RESULT").toint())
    }
}
