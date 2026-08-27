package com.winter.muplayer.plugin.bytecode

import com.winter.muplayer.plugin.PluginException
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import org.luaj.vm2.Globals
import org.luaj.vm2.compiler.DumpState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * 预编译字节码编译器：Lua 源码 → LuaJ Prototype → 二进制 chunk（.ljbc）。
 *
 * 使用 LuaJ 自带的 `compilePrototype` + `Prototype.dump()`，产物带 `\x1bLua` 签名头，
 * 可被 `Globals.load(InputStream, name)`（Undumper）直接识别执行。
 */
object BytecodeCompiler {

    /**
     * 编译 Lua 源码为字节码。
     *
     * @param source    Lua 源码文本
     * @param chunkName chunk 名（用于错误信息；建议传入口脚本相对路径）
     */
    fun compile(source: String, chunkName: String): ByteArray {
        val globals: Globals = LuaRuntimeFactory.newGlobals()
        val prototype = try {
            globals.compilePrototype(
                ByteArrayInputStream(source.toByteArray(StandardCharsets.UTF_8)),
                chunkName
            )
        } catch (e: Exception) {
            throw PluginException("Lua 源码编译失败 ($chunkName): ${e.message}", e)
        }
        val out = ByteArrayOutputStream()
        try {
            // DumpState 输出带 \x1bLua 签名头的二进制 chunk（.ljbc）
            DumpState.dump(prototype, out, false)
        } catch (e: Exception) {
            throw PluginException("Lua 字节码序列化失败 ($chunkName): ${e.message}", e)
        }
        return out.toByteArray()
    }
}
