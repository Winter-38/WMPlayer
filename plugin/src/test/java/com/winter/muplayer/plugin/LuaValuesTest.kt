package com.winter.muplayer.plugin

import com.winter.muplayer.model.Track
import com.winter.muplayer.plugin.bridge.LuaValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/**
 * 验证宿主 ↔ 插件数据转换：Track 双向映射、Track 列表、宿主值→Lua。
 */
class LuaValuesTest {

    private val track = Track(
        id = 1001L,
        title = "十年",
        artist = "陈奕迅",
        album = "黑白灰",
        duration = 210_000L,
        uri = "content://media/external/audio/media/1001",
        albumId = 55L,
        fileSize = 8_000_000L,
        dateAdded = 1_700_000_000L
    )

    @Test
    fun `应用 Track 转插件标准 Track 表`() {
        val t = LuaValues.toTrackTable(track)
        assertEquals(1001, t.get("id").tolong())
        assertEquals("local", t.get("source").tojstring())
        assertEquals("十年", t.get("title").tojstring())
        assertEquals("陈奕迅", t.get("artist").tojstring())
        assertEquals("黑白灰", t.get("album").tojstring())
        assertEquals(210_000, t.get("duration").tolong())
        assertEquals("content://media/external/audio/media/1001", t.get("url").tojstring())
        val extra = t.get("extra").checktable()
        assertEquals(55, extra.get("albumId").tolong())
    }

    @Test
    fun `插件 Track 表转应用 Track`() {
        val t = LuaTable()
        t.set("id", LuaValue.valueOf(7.0))
        t.set("title", LuaValue.valueOf("T"))
        t.set("artist", LuaValue.valueOf("A"))
        t.set("album", LuaValue.valueOf("Al"))
        t.set("duration", LuaValue.valueOf(12345.0))
        t.set("url", LuaValue.valueOf("http://example/a.mp3"))

        val restored = LuaValues.fromTrackTable(t)
        assertEquals(7L, restored.id)
        assertEquals("T", restored.title)
        assertEquals("A", restored.artist)
        assertEquals("Al", restored.album)
        assertEquals(12345L, restored.duration)
        assertEquals("http://example/a.mp3", restored.uri)
    }

    @Test
    fun `Track 列表转 Lua 数组表`() {
        val list = LuaTable()
        list.set(1, LuaValues.toTrackTable(track))
        val t2 = LuaValues.toTrackTable(track.copy(id = 2002L))
        list.set(2, t2)

        val tracks = LuaValues.toTrackList(list)
        assertEquals(2, tracks.size)
        assertEquals(1001L, tracks[0].id)
        assertEquals(2002L, tracks[1].id)
        assertNotNull(tracks[0].uri)
        assertTrue(tracks[0].uri.isNotEmpty())
    }
}
