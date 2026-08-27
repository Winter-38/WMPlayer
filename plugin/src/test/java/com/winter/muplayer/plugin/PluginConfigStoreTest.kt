package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.bridge.PluginConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.File

/**
 * 验证插件持久化配置（plugin.getConfig / plugin.setConfig）：
 * 支持 string / number / boolean / table，写入后可从磁盘恢复。
 */
class PluginConfigStoreTest {

    private fun tmpDir(): File = File.createTempFile("cfg-test", "").apply { delete(); mkdirs() }

    @Test
    fun `写入并读取各类型配置`() {
        val dir = tmpDir()
        val store = PluginConfigStore(dir, "test.plugin")

        assertEquals(LuaValue.NIL, store.get("missing"))

        store.set("name", LuaValue.valueOf("歌词助手"))
        store.set("count", LuaValue.valueOf(42))
        store.set("ratio", LuaValue.valueOf(0.5))
        store.set("enabled", LuaValue.valueOf(true))

        assertEquals("歌词助手", store.get("name").tojstring())
        assertEquals(42, store.get("count").toint())
        assertEquals(0.5, store.get("ratio").todouble(), 0.001)
        assertTrue(store.get("enabled").toboolean())
    }

    @Test
    fun `set 返回旧值供 onConfigChanged 使用`() {
        val dir = tmpDir()
        val store = PluginConfigStore(dir, "test.plugin")

        assertEquals(LuaValue.NIL, store.set("key", LuaValue.valueOf("first")))
        val old = store.set("key", LuaValue.valueOf("second"))
        assertEquals("first", old.tojstring())
        assertEquals("second", store.get("key").tojstring())
    }

    @Test
    fun `table 配置可持久化与还原`() {
        val dir = tmpDir()
        val store = PluginConfigStore(dir, "test.plugin")

        val table = LuaTable()
        table.set("title", LuaValue.valueOf("Song"))
        table.set("artist", LuaValue.valueOf("Artist"))
        table.set("duration", LuaValue.valueOf(180000))
        store.set("track", table)

        val restored = store.get("track").checktable()
        assertEquals("Song", restored.get("title").tojstring())
        assertEquals("Artist", restored.get("artist").tojstring())
        assertEquals(180000, restored.get("duration").toint())
    }

    @Test
    fun `重新实例化从磁盘恢复配置`() {
        val dir = tmpDir()
        PluginConfigStore(dir, "test.plugin")
            .set("persisted", LuaValue.valueOf("yes"))

        val reloaded = PluginConfigStore(dir, "test.plugin")
        assertEquals("yes", reloaded.get("persisted").tojstring())
    }
}
