package com.winter.muplayer.plugin.bytecode

import com.winter.muplayer.plugin.PluginConfig
import com.winter.muplayer.plugin.model.PluginDescriptor
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/**
 * 预编译字节码缓存：
 *
 * - **命中**：直接 mmap 字节码文件（虚拟内存映射，按需换入物理内存）加载执行，
 *   省去每次启动重新编译源码的开销；
 * - **未命中**：源码 → [BytecodeCompiler] 预编译 → 写盘 → 加载。
 *
 * 缓存键 = SHA-256(源码文本)，源码变更后键变化，天然失效，无需版本管理。
 */
object BytecodeCache {

    private const val EXT = ".ljbc"

    /**
     * 获取可执行的 Lua chunk：优先字节码缓存（mmap），否则编译并回写缓存。
     *
     * @param globals    目标运行时环境（字节码在执行时绑定环境）
     * @param descriptor 插件描述
     * @param config     插件配置（缓存目录）
     */
    fun obtainChunk(globals: Globals, descriptor: PluginDescriptor, config: PluginConfig): LuaValue {
        val sourceFile = descriptor.entryFile
        check(sourceFile.isFile) {
            "插件 ${descriptor.id} 入口不存在或不是文件: ${descriptor.entry}"
        }
        val source = sourceFile.readText()
        val cacheFile = cacheFileFor(source, config)

        // 1) 命中：mmap 虚拟内存按需换入
        if (cacheFile.exists()) {
            val mapped = MappedBytecodeStore.map(cacheFile)
            if (mapped != null) {
                return globals.loadMapped(mapped, descriptor.entry)
            }
            // mmap 失败（文件损坏）则回退重新编译
            cacheFile.delete()
        }

        // 2) 未命中：预编译 + 写缓存
        val bytes = BytecodeCompiler.compile(source, descriptor.entry)
        config.bytecodeCacheDir.mkdirs()
        cacheFile.writeBytes(bytes)
        // mode="bt"：Undumper/编译器自动识别二进制 chunk
        return globals.load(ByteArrayInputStream(bytes), descriptor.entry, "bt", globals)
    }

    /** 源码变更时主动使缓存失效（供卸载插件等场景调用）。 */
    fun invalidate(source: String, config: PluginConfig) {
        cacheFileFor(source, config).delete()
    }

    /** 清理不再被任何已安装插件引用的缓存文件。 */
    fun prune(config: PluginConfig, activeSources: Set<String>) {
        val activeKeys = activeSources.mapTo(HashSet()) { keyOf(it) }
        config.bytecodeCacheDir.listFiles()?.forEach { file ->
            val key = file.name.removeSuffix(EXT)
            if (key !in activeKeys) file.delete()
        }
    }

    private fun cacheFileFor(source: String, config: PluginConfig): File =
        File(config.bytecodeCacheDir, "${keyOf(source)}$EXT")

    private fun keyOf(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
