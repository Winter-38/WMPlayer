package com.winter.muplayer.config

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 配置编译缓存 —— 将 ComponentLayout + CssRuleTable 编译为二进制格式，
 * 后续启动直接加载，跳过 JSON + CSS 解析。
 *
 * 安全：手写二进制格式，只读写 String/Int/Boolean，无反射无代码执行路径。
 * 文件末尾附带 SHA-256 校验，防外部篡改。
 *
 * 路径：<configDir>/layout.cache
 */
object BinaryCache {

    private const val MAGIC = "WMPC"
    private const val VERSION: Byte = 1
    private const val CACHE_FILENAME = "layout.cache"

    // Extra value 类型标记
    private const val TYPE_STRING = 0
    private const val TYPE_BOOLEAN = 1
    private const val TYPE_INT = 2
    private const val TYPE_LONG = 3
    private const val TYPE_DOUBLE = 4
    private const val TYPE_NULL = 5
    private const val TYPE_CHILDREN = 6

    /**
     * 写入编译缓存。同时写 SHA-256 校验到文件末尾。
     */
    fun write(configDir: File, layout: ComponentLayout, css: CssRuleTable) {
        val bytes = toBytes(layout, css)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        val cacheFile = File(configDir, CACHE_FILENAME)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(bytes + hash)
    }

    /**
     * 尝试读取编译缓存。
     * - 文件不存在 → null
     * - SHA-256 校验失败 → 删除缓存 + 返回 null（可能被篡改或损坏）
     * - 校验通过 → 返回 (ComponentLayout, CssRuleTable)
     */
    fun tryRead(configDir: File): Pair<ComponentLayout, CssRuleTable>? {
        val cacheFile = File(configDir, CACHE_FILENAME)
        if (!cacheFile.isFile) return null

        val allBytes = try { cacheFile.readBytes() } catch (_: Exception) { return null }
        if (allBytes.size < 33) return null // 至少 1 字节数据 + 32 字节 hash

        val data = allBytes.copyOfRange(0, allBytes.size - 32)
        val storedHash = allBytes.copyOfRange(allBytes.size - 32, allBytes.size)

        val computedHash = MessageDigest.getInstance("SHA-256").digest(data)
        if (!storedHash.contentEquals(computedHash)) {
            // 校验失败 → 删除损坏/篡改的缓存
            cacheFile.delete()
            return null
        }

        return try {
            fromBytes(data)
        } catch (_: Exception) {
            cacheFile.delete()
            null
        }
    }

    /**
     * 判断缓存是否最新的（比所有源文件都新）。
     */
    fun isFresh(configDir: File): Boolean {
        val cacheFile = File(configDir, CACHE_FILENAME)
        if (!cacheFile.isFile) return false
        val cacheTime = cacheFile.lastModified()
        val files = configDir.listFiles() ?: return false
        // 缓存必须比所有 .json 和 .css 文件都新
        for (f in files) {
            if (!f.isFile) continue
            val ext = f.extension
            if (ext == "json" || ext == "css") {
                if (f.lastModified() > cacheTime) return false
            }
        }
        return true
    }

    /** 删除缓存（热重载时调用） */
    fun invalidate(configDir: File) {
        File(configDir, CACHE_FILENAME).delete()
    }

    // ── 序列化 ──

    private fun toBytes(layout: ComponentLayout, css: CssRuleTable): ByteArray {
        val baos = java.io.ByteArrayOutputStream(4096)
        DataOutputStream(baos).use { out ->
            out.writeBytes(MAGIC)
            out.writeByte(VERSION.toInt())
            writeSlots(out, layout.slots)
            writeSlots(out, layout.fullPlayerSlots)
            writeCss(out, css.rules)
        }
        return baos.toByteArray()
    }

    private fun writeSlots(out: DataOutputStream, slots: Map<String, List<ComponentEntry>>) {
        out.writeInt(slots.size)
        for ((name, components) in slots) {
            out.writeUTF(name)
            writeComponents(out, components)
        }
    }

    private fun writeComponents(out: DataOutputStream, components: List<ComponentEntry>) {
        out.writeInt(components.size)
        for (entry in components) {
            out.writeUTF(entry.id)
            out.writeBoolean(entry.cid != null)
            entry.cid?.let { out.writeUTF(it) }
            out.writeBoolean(entry.isCustom)
            writeExtra(out, entry.extra)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeExtra(out: DataOutputStream, extra: Map<String, Any?>) {
        out.writeInt(extra.size)
        for ((key, value) in extra) {
            out.writeUTF(key)
            when (value) {
                null -> { out.writeByte(TYPE_NULL) }
                is String -> {
                    out.writeByte(TYPE_STRING)
                    out.writeUTF(value)
                }
                is Boolean -> {
                    out.writeByte(TYPE_BOOLEAN)
                    out.writeBoolean(value)
                }
                is Int -> {
                    out.writeByte(TYPE_INT)
                    out.writeInt(value)
                }
                is Long -> {
                    out.writeByte(TYPE_LONG)
                    out.writeLong(value)
                }
                is Double -> {
                    out.writeByte(TYPE_DOUBLE)
                    out.writeDouble(value)
                }
                is Map<*, *> -> {
                    // children 子 slot 字典
                    out.writeByte(TYPE_CHILDREN)
                    val children = value as Map<String, List<ComponentEntry>>
                    out.writeInt(children.size)
                    for ((childName, childComps) in children) {
                        out.writeUTF(childName)
                        writeComponents(out, childComps)
                    }
                }
                else -> {
                    // fallback：转字符串
                    out.writeByte(TYPE_STRING)
                    out.writeUTF(value.toString())
                }
            }
        }
    }

    private fun writeCss(out: DataOutputStream, rules: Map<String, Map<String, String>>) {
        out.writeInt(rules.size)
        for ((selector, props) in rules) {
            out.writeUTF(selector)
            out.writeInt(props.size)
            for ((key, value) in props) {
                out.writeUTF(key)
                out.writeUTF(value)
            }
        }
    }

    // ── 反序列化 ──

    private fun fromBytes(data: ByteArray): Pair<ComponentLayout, CssRuleTable> {
        val bais = java.io.ByteArrayInputStream(data)
        DataInputStream(bais).use { `in` ->
            // 验证 Magic
            val magic = ByteArray(4)
            `in`.readFully(magic)
            if (String(magic) != MAGIC) throw IllegalArgumentException("Bad magic")

            val version = `in`.readByte()
            if (version != VERSION) throw IllegalArgumentException("Unsupported version: $version")

            val slots = readSlots(`in`)
            val fullPlayerSlots = readSlots(`in`)
            val cssRules = readCss(`in`)

            val layout = ComponentLayout(
                slots = if (slots.isNotEmpty()) slots else ComponentLayout.defaultSlots,
                fullPlayerSlots = if (fullPlayerSlots.isNotEmpty()) fullPlayerSlots else ComponentLayout.defaultFullPlayerSlots,
            )
            return layout to CssRuleTable(rules = cssRules)
        }
    }

    private fun readSlots(`in`: DataInputStream): Map<String, List<ComponentEntry>> {
        val count = `in`.readInt()
        val result = linkedMapOf<String, List<ComponentEntry>>()
        for (i in 0 until count) {
            val name = `in`.readUTF()
            val components = readComponents(`in`)
            result[name] = components
        }
        return result
    }

    private fun readComponents(`in`: DataInputStream): List<ComponentEntry> {
        val count = `in`.readInt()
        return List(count) {
            val id = `in`.readUTF()
            val cid = if (`in`.readBoolean()) `in`.readUTF() else null
            val isCustom = `in`.readBoolean()
            val extra = readExtra(`in`)
            ComponentEntry(id, cid = cid, extra = extra, isCustom = isCustom)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readExtra(`in`: DataInputStream): Map<String, Any?> {
        val count = `in`.readInt()
        val result = mutableMapOf<String, Any?>()
        for (i in 0 until count) {
            val key = `in`.readUTF()
            val type = `in`.readByte().toInt()
            val value: Any? = when (type) {
                TYPE_NULL -> null
                TYPE_STRING -> `in`.readUTF()
                TYPE_BOOLEAN -> `in`.readBoolean()
                TYPE_INT -> `in`.readInt()
                TYPE_LONG -> `in`.readLong()
                TYPE_DOUBLE -> `in`.readDouble()
                TYPE_CHILDREN -> {
                    val childCount = `in`.readInt()
                    val children = linkedMapOf<String, List<ComponentEntry>>()
                    for (j in 0 until childCount) {
                        val childName = `in`.readUTF()
                        children[childName] = readComponents(`in`)
                    }
                    children
                }
                else -> throw IllegalArgumentException("Unknown extra type: $type")
            }
            result[key] = value
        }
        return result
    }

    private fun readCss(`in`: DataInputStream): Map<String, Map<String, String>> {
        val count = `in`.readInt()
        val result = mutableMapOf<String, Map<String, String>>()
        for (i in 0 until count) {
            val selector = `in`.readUTF()
            val propCount = `in`.readInt()
            val props = mutableMapOf<String, String>()
            for (j in 0 until propCount) {
                val key = `in`.readUTF()
                val value = `in`.readUTF()
                props[key] = value
            }
            result[selector] = props
        }
        return result
    }
}
