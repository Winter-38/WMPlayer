package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.bytecode.BytecodeCache
import com.winter.muplayer.plugin.bytecode.BytecodeCompiler
import com.winter.muplayer.plugin.bytecode.MappedBytecodeStore
import com.winter.muplayer.plugin.bytecode.loadMapped
import com.winter.muplayer.plugin.model.PluginDescriptor
import com.winter.muplayer.plugin.runtime.LuaRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaValue
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 验证「预编译字节码缓存」与「mmap 虚拟内存按需换入」两条执行路径，
 * 以及 BytecodeCache 的命中/未命中分支。
 */
class BytecodePipelineTest {

    private val source = """
        return {
            add = function(a, b) return a + b end,
            hello = function() return "hi" end
        }
    """.trimIndent()

    private fun tmpDir(): File = File.createTempFile("plugin-test", "").apply {
        delete()
        mkdirs()
    }

    @Test
    fun `源码编译产物可被加载执行`() {
        val bytes = BytecodeCompiler.compile(source, "math.lua")
        assertTrue("编译产物应带 Lua 签名头", bytes.size > 8)

        val globals = LuaRuntimeFactory.newGlobals()
        val result = globals.load(ByteArrayInputStream(bytes), "math.ljbc", "bt", globals).call()
        val add = result.arg1().checktable().get("add")
        val sum = add.invoke(
            LuaValue.varargsOf(arrayOf(LuaValue.valueOf(2), LuaValue.valueOf(3)))
        ).arg1().toint()
        assertEquals(5, sum)
    }

    @Test
    fun `mmap 映射的字节码可直接执行`() {
        val bytes = BytecodeCompiler.compile(source, "math.lua")
        val file = File(tmpDir(), "cache.ljbc")
        file.writeBytes(bytes)

        val mapped = MappedBytecodeStore.map(file)
        assertTrue("mmap 应成功", mapped != null)

        val globals = LuaRuntimeFactory.newGlobals()
        val result = globals.loadMapped(mapped!!, "math.ljbc").call()
        val hello = result.arg1().checktable().get("hello")
        assertEquals("hi", hello.invoke().tojstring())
    }

    @Test
    fun `缓存未命中时预编译并写盘，命中时走 mmap`() {
        val dir = tmpDir()
        val config = PluginConfig(
            pluginsDir = dir,
            bytecodeCacheDir = File(dir, ".bytecode"),
            cacheDir = File(dir, "downloads")
        )
        val descriptor = PluginDescriptor(
            id = "test.math",
            name = "Math",
            version = "1.0.0",
            entry = "main.lua",
            runtime = RuntimeKind.SHARED,
            events = emptyList(),
            dir = dir
        )
        File(dir, "main.lua").writeText(source)

        val globals = LuaRuntimeFactory.newGlobals()

        // 首次：编译 + 写缓存
        val first = BytecodeCache.obtainChunk(globals, descriptor, config)
        assertEquals(5, first.call().arg1().checktable().get("add")
            .invoke(LuaValue.varargsOf(arrayOf(LuaValue.valueOf(2), LuaValue.valueOf(3)))).arg1().toint())
        val cacheFiles = config.bytecodeCacheDir.listFiles()
        assertTrue("应生成 .ljbc 缓存文件", cacheFiles != null && cacheFiles!!.size == 1)

        // 再次：命中 mmap（同一结果）
        val second = BytecodeCache.obtainChunk(globals, descriptor, config)
        assertEquals(5, second.call().arg1().checktable().get("add")
            .invoke(LuaValue.varargsOf(arrayOf(LuaValue.valueOf(2), LuaValue.valueOf(3)))).arg1().toint())

        // 源码变更后缓存键变化，重新编译
        File(dir, "main.lua").writeText(source.replace("return a + b", "return a - b"))
        val third = BytecodeCache.obtainChunk(globals, descriptor, config)
        assertEquals(-1, third.call().arg1().checktable().get("add")
            .invoke(LuaValue.varargsOf(arrayOf(LuaValue.valueOf(2), LuaValue.valueOf(3)))).arg1().toint())
        assertEquals("源码变更应新增缓存文件", 2, config.bytecodeCacheDir.listFiles()!!.size)
    }
}
