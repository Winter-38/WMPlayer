package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.bridge.LuaJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * 验证 json 转换：Lua 数组表 → JSON 数组、对象表 → JSON 对象、parse 双向还原。
 */
class LuaJsonTest {

    private fun arrayOf(vararg values: Int): LuaTable {
        val t = LuaTable()
        values.forEachIndexed { i, v -> t.set(i + 1, LuaValue.valueOf(v)) }
        return t
    }

    @Test
    fun `数组表序列化为 JSON 数组`() {
        val t = arrayOf(1, 2, 3)
        assertEquals("[1,2,3]", LuaJson.toJson(t).toString())
    }

    @Test
    fun `对象表序列化为 JSON 对象`() {
        val t = LuaTable()
        t.set("title", LuaValue.valueOf("Song"))
        t.set("duration", LuaValue.valueOf(180))
        val json = LuaJson.toJson(t).toString()
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"title\":\"Song\""))
        assertTrue(json.contains("\"duration\":180"))
    }

    @Test
    fun `嵌套数组与对象`() {
        val inner = LuaTable()
        inner.set("name", LuaValue.valueOf("x"))
        val arr = arrayOf(1, 2)
        arr.set(3, inner)

        val json = LuaJson.toJson(arr).toString()
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"name\":\"x\""))
    }

    @Test
    fun `混合表按对象处理并跳过数字键`() {
        val t = LuaTable()
        t.set(1, LuaValue.valueOf("skip"))
        t.set("key", LuaValue.valueOf("keep"))
        val json = LuaJson.toJson(t).toString()
        assertFalse(json.contains("skip"))
        assertTrue(json.contains("\"key\":\"keep\""))
    }

    @Test
    fun `空表视为对象`() {
        assertEquals("{}", LuaJson.toJson(LuaTable()).toString())
        assertFalse(LuaJson.isArrayTable(LuaTable()))
    }

    @Test
    fun `isArrayTable 判定`() {
        assertTrue(LuaJson.isArrayTable(arrayOf(1, 2, 3)))
        // 空洞 → 非数组
        val hole = LuaTable()
        hole.set(1, LuaValue.valueOf(1))
        hole.set(3, LuaValue.valueOf(3))
        assertFalse(LuaJson.isArrayTable(hole))
        // 非连续键（1..n + n+1）→ 非数组
        val extra = arrayOf(1, 2)
        extra.set(4, LuaValue.valueOf(4))
        assertFalse(LuaJson.isArrayTable(extra))
        // 字符串键 → 非数组
        val mixed = arrayOf(1)
        mixed.set("k", LuaValue.valueOf("v"))
        assertFalse(LuaJson.isArrayTable(mixed))
    }

    @Test
    fun `parse 还原 JSON 数组与对象`() {
        val arr = LuaJson.toLua(org.json.JSONArray(listOf(1, 2, 3)))
        assertEquals(3, arr.length())
        assertEquals(1, arr.get(1).toint())
        assertEquals(2, arr.get(2).toint())
        assertEquals(3, arr.get(3).toint())

        val obj = LuaJson.toLua(org.json.JSONObject("{\"a\":1,\"b\":\"x\"}"))
        assertEquals(1, obj.get("a").toint())
        assertEquals("x", obj.get("b").tojstring())
    }

    @Test
    fun `数组往返 roundtrip`() {
        val t = arrayOf(10, 20)
        val json = LuaJson.toJson(t).toString()
        val back = LuaJson.toLua(org.json.JSONArray(json))
        assertEquals(10, back.get(1).toint())
        assertEquals(20, back.get(2).toint())
        assertTrue(LuaJson.isArrayTable(back.checktable()))
    }
}
