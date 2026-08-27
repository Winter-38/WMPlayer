package com.winter.muplayer.plugin.bytecode

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * 虚拟内存字节码存储：把 .ljbc 字节码文件 mmap 到虚拟地址空间，
 * **按需使用物理内存**。
 *
 * `FileChannel.map(READ_ONLY)` 建立文件 → 进程虚拟地址空间的映射，OS 分页机制
 * 只在真正访问（执行）时把对应页换入物理内存，未访问部分仅占虚拟地址空间。
 * 对较大的字节码缓存，这避免了"全量读入内存"的开销。
 *
 * 注意：Android 上映射区域的回收由 GC 管理，映射生命周期跟随 ByteBuffer 对象。
 */
object MappedBytecodeStore {

    /** 把字节码文件映射为只读 [ByteBuffer]；映射失败（文件损坏等）返回 null。 */
    fun map(file: java.io.File): ByteBuffer? = try {
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            if (channel.size() == 0L) {
                null
            } else {
                channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
        }
    } catch (e: Exception) {
        null
    }

    /** 把 mmap 缓冲区包装成 [Globals.load] 可读的流（不复制数据，直接读映射页）。 */
    fun toInputStream(buffer: ByteBuffer): java.io.InputStream = ByteBufferInputStream(buffer)

    private class ByteBufferInputStream(private val buffer: ByteBuffer) : java.io.InputStream() {
        override fun read(): Int =
            if (buffer.hasRemaining()) buffer.get().toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!buffer.hasRemaining()) return -1
            val n = minOf(len, buffer.remaining())
            buffer.get(b, off, n)
            return n
        }
    }
}

/** 便捷函数：从 mmap 缓冲区直接加载并返回 Lua chunk（Undumper 自动识别二进制）。 */
fun Globals.loadMapped(buffer: ByteBuffer, chunkName: String): LuaValue =
    load(MappedBytecodeStore.toInputStream(buffer), chunkName, "bt", this)
