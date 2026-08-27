package com.winter.muplayer.plugin.bridge

import com.winter.muplayer.model.Track
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs

/**
 * Lua 值 ↔ 宿主值转换（宿主 API 桥专用）。
 *
 * 与 runtime 包里的 [com.winter.muplayer.plugin.runtime.LuaConversions] 分工：
 * 后者服务事件负载，这里额外提供插件 Track ↔ 应用 Track 的双向映射，
 * 以及 Lua table 的字段读取辅助。
 */
object LuaValues {

    // ==================== 宿主值 → Lua ====================

    fun toLuaValue(value: Any?): LuaValue = when (value) {
        null -> LuaValue.NIL
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Double -> LuaValue.valueOf(value)
        is Float -> LuaValue.valueOf(value.toDouble())
        is String -> LuaValue.valueOf(value)
        is Map<*, *> -> LuaTable().also { t ->
            value.forEach { (k, v) -> t.set(k.toString(), toLuaValue(v)) }
        }
        is List<*> -> LuaTable().also { t ->
            value.forEachIndexed { i, v -> t.set(i + 1, toLuaValue(v)) }
        }
        else -> LuaValue.valueOf(value.toString())
    }

    /** 应用 Track → 插件标准 Track 表（贴合插件侧 Track 约定）。 */
    fun toTrackTable(track: Track): LuaTable {
        val extra = LuaTable()
        extra.set("albumId", LuaValue.valueOf(track.albumId.toDouble()))
        extra.set("fileSize", LuaValue.valueOf(track.fileSize.toDouble()))
        extra.set("dateAdded", LuaValue.valueOf(track.dateAdded.toDouble()))
        val t = LuaTable()
        t.set("id", LuaValue.valueOf(track.id.toDouble()))
        t.set("source", LuaValue.valueOf("local"))
        t.set("title", LuaValue.valueOf(track.title))
        t.set("artist", LuaValue.valueOf(track.artist))
        t.set("album", LuaValue.valueOf(track.album))
        t.set("duration", LuaValue.valueOf(track.duration.toDouble()))
        t.set("url", LuaValue.valueOf(track.uri))
        t.set("extra", extra)
        return t
    }

    // ==================== Lua → 宿主 ====================

    /** 读取 Lua table 的字符串字段。 */
    fun tableString(table: LuaTable, key: String, default: String = ""): String =
        table.get(key).optjstring(default)

    /** 读取 Lua table 的数字字段（毫秒），缺省返回 [default]。 */
    fun tableLong(table: LuaTable, key: String, default: Long = 0L): Long =
        table.get(key).tojstring().toLongOrNull() ?: default

    /** 插件 Track 表 → 应用 Track（本地播放，url 作为播放地址）。 */
    fun fromTrackTable(table: LuaTable): Track {
        val id = tableLong(table, "id")
        val title = tableString(table, "title").ifBlank { "未知" }
        val artist = tableString(table, "artist").ifBlank { "未知艺术家" }
        val album = tableString(table, "album").ifBlank { "未知专辑" }
        val duration = tableLong(table, "duration")
        val uri = tableString(table, "url")
            .ifBlank { tableString(table, "uri") }
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = uri
        )
    }

    /** 把 Lua 数组表（1..n）转成宿主列表。 */
    fun toTrackList(table: LuaTable): List<Track> {
        val result = mutableListOf<Track>()
        var i = 1
        while (true) {
            val item = table.get(i)
            if (item.isnil()) break
            item.opttable(null)?.let { result.add(fromTrackTable(it)) }
            i++
        }
        return result
    }

    /** Varargs → Lua 数组表（供回调/事件 data 使用）。 */
    fun toArgsTable(args: Varargs): LuaTable {
        val t = LuaTable()
        for (i in 1..args.narg()) t.set(i, args.arg(i))
        return t
    }
}
