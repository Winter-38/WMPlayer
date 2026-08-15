package com.winter.muplayer.core.scanner

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.winter.muplayer.model.Track
import java.io.File
import java.util.UUID

/**
 * 本地音乐扫描器～负责把手机里的音乐文件找出来！
 *
 * 磁盘缓存：
 * - 扫描结果持久化到 [CACHE_FILE]（JSON 格式），init 时直接加载
 * - 每次 [scan] 完成后自动更新磁盘缓存
 * - [invalidateCache] 清除内存 + 磁盘缓存
 *
 * 扫描策略：
 * - Android 10 以上（API 29+）：用 [MediaStore] 查
 * - Android 9 以下（API 28-）：遍历文件系统
 */
class LocalMusicScanner(
    private val context: Context,
    val audioExtensions: Set<String> = DEFAULT_AUDIO_EXTENSIONS
) {

    companion object {
        val DEFAULT_AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "m4a", "ogg", "aac", "wma", "ape", "aiff", "opus"
        )
        private const val CACHE_FILE = "local_music_cache.json"
        private const val MAX_TRACKS = 3000
    }

    /** 上一次扫描的完整结果（磁盘缓存的镜像） */
    private var cachedTracks: List<Track> = emptyList()
    private var cacheLoaded = false

    /** 文件系统模式下要去哪些目录翻歌 */
    private val fileSystemScanDirs = listOf(
        "Music", "Download", "Podcasts", "Ringtones",
        "Alarms", "Notifications", "Sounds", "Recordings"
    )

    init {
        // 缓存改为懒加载：不阻塞构造主线程，首次 getCachedTracks() 或 scan() 时再加载
    }

    // ==================== 公开 API ====================

    /**
     * 返回当前缓存中的歌单（不触发扫描，首次调用时从磁盘加载）。
     * 可用于在 UI 上立即显示上次的结果。
     */
    fun getCachedTracks(): List<Track> {
        if (!cacheLoaded) {
            cachedTracks = loadFromDisk()
            cacheLoaded = true
        }
        return cachedTracks
    }

    /**
     * 快速扫描 — 只扫前 [count] 首，不缓存，用于快速首屏显示。
     */
    fun scanFast(count: Int = 300): List<Track> {
        return scanMediaStore(count)
    }

    /**
     * 全量扫描 — 扫完更新磁盘缓存。
     * 如果内存缓存有效则直接返回（同一 session 内不重复扫）。
     */
    fun scanFull(): List<Track> {
        // 同一 session 内缓存有效则直接返回
        if (cacheLoaded && cachedTracks.isNotEmpty()) return cachedTracks

        val tracks = if (Build.VERSION.SDK_INT >= 29) {
            scanMediaStore(MAX_TRACKS)
        } else {
            scanFileSystem()
        }

        cachedTracks = tracks
        cacheLoaded = true
        saveToDisk(tracks)
        return tracks
    }

    /** 清掉缓存，下次 [scan] 会重新扫并更新磁盘缓存 */
    fun invalidateCache() {
        cacheLoaded = false
    }

    /** 仅清除磁盘缓存 */
    private fun invalidateDiskCache() {
        getCacheFile().delete()
    }

    // ==================== 磁盘持久化 ====================

    private fun getCacheFile(): File = File(context.filesDir, CACHE_FILE)

    private fun loadFromDisk(): List<Track> {
        val file = getCacheFile()
        if (!file.exists()) return emptyList()
        return try {
            // 流式手解析：不构建 DOM 树，按行扫描 Json 对象
            val text = file.readText()
            val result = mutableListOf<Track>()
            // 简单扫描 { "id": ..., "title": "..." } 模式
            var i = text.indexOf('{', text.indexOf("\"tracks\""))
            while (i >= 0) {
                val end = text.indexOf('}', i)
                if (end < 0) break
                val obj = text.substring(i, end + 1)
                fun field(name: String): String {
                    val key = "\"$name\""
                    val ki = obj.indexOf(key) ?: return ""
                    val vi = ki + key.length
                    val colon = obj.indexOf(':', vi) ?: return ""
                    val start = colon + 1
                    val trimmed = obj.drop(start).trimStart()
                    return when {
                        trimmed.startsWith('"') -> {
                            val close = trimmed.indexOf('"', 1)
                            if (close > 0) trimmed.substring(1, close) else ""
                        }
                        else -> trimmed.takeWhile { it.isDigit() || it == '-' }
                    }
                }
                val id = field("id").toLongOrNull() ?: 0L
                val title = field("title")
                result.add(Track(
                    id = id,
                    title = title.ifEmpty { "未知" },
                    artist = field("artist").ifEmpty { "未知艺术家" },
                    album = field("album").ifEmpty { "未知专辑" },
                    duration = field("duration").toLongOrNull() ?: 0L,
                    uri = field("uri"),
                    albumId = field("albumId").toLongOrNull() ?: 0L,
                    fileSize = field("fileSize").toLongOrNull() ?: 0L,
                    dateAdded = field("dateAdded").toLongOrNull() ?: 0L
                ))
                i = text.indexOf('{', end)
            }
            result
        } catch (_: Exception) {
            file.delete()
            emptyList()
        }
    }

    private fun saveToDisk(tracks: List<Track>) {
        try {
            val sb = StringBuilder(1024 * 1024) // 预分配 1MB
            sb.append("{\"version\":1,\"count\":").append(tracks.size).append(",\"tracks\":[")
            for ((idx, t) in tracks.withIndex()) {
                if (idx > 0) sb.append(',')
                sb.append("{\"id\":").append(t.id)
                    .append(",\"title\":").append(escape(t.title))
                    .append(",\"artist\":").append(escape(t.artist))
                    .append(",\"album\":").append(escape(t.album))
                    .append(",\"duration\":").append(t.duration)
                    .append(",\"uri\":").append(escape(t.uri))
                    .append(",\"albumId\":").append(t.albumId)
                    .append(",\"fileSize\":").append(t.fileSize)
                    .append(",\"dateAdded\":").append(t.dateAdded)
                    .append('}')
            }
            sb.append("]}")
            getCacheFile().writeText(sb.toString())
        } catch (_: Exception) { }
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ==================== MediaStore 扫描 ====================

    private fun scanMediaStore(limit: Int = MAX_TRACKS): List<Track> {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val colId = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val colTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val colArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val colAlbum = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val colDuration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val colData = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val colAlbumId = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val colSize = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val colDateAdded = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

            var rowCount = 0
            while (rowCount < limit && cursor.moveToNext()) {
                rowCount++
                val data = cursor.getString(colData) ?: continue
                val id = cursor.getLong(colId)
                val title = cursor.getString(colTitle)?.takeIf { it.isNotBlank() }
                    ?: File(data).nameWithoutExtension
                val artist = cursor.getString(colArtist)?.takeIf { it.isNotBlank() }
                    ?: "未知艺术家"
                val album = cursor.getString(colAlbum)?.takeIf { it.isNotBlank() }
                    ?: "未知专辑"
                val duration = cursor.getLong(colDuration).coerceAtLeast(0L)

                val uri = if (Build.VERSION.SDK_INT >= 30) {
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                } else {
                    "file://$data"
                }

                val albumId = cursor.getLong(colAlbumId).coerceAtLeast(0L)

                tracks.add(
                    Track(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = uri,
                        albumId = albumId,
                        fileSize = cursor.getLong(colSize).coerceAtLeast(0L),
                        dateAdded = cursor.getLong(colDateAdded).coerceAtLeast(0L)
                    )
                )
            }
        }
        return tracks
    }

    // ==================== 文件系统扫描 ====================

    private fun scanFileSystem(): List<Track> {
        val tracks = mutableListOf<Track>()
        val extStorage = Environment.getExternalStorageDirectory()
        val dirs = ArrayDeque<File>()

        for (subDir in fileSystemScanDirs) {
            val dir = File(extStorage, subDir)
            if (dir.isDirectory) dirs.add(dir)
        }

        val externalDirs = context.getExternalFilesDirs(null)
        for (extDir in externalDirs) {
            if (extDir != null && extDir.absolutePath.contains("Android", ignoreCase = false)) continue
            val parent = extDir?.parentFile
            if (parent != null && parent.isDirectory && !dirs.contains(parent)) {
                for (subDir in fileSystemScanDirs) {
                    val dir = File(parent, subDir)
                    if (dir.isDirectory) dirs.add(dir)
                }
            }
        }

        val extRegex = audioExtensions.joinToString("|") { Regex.escape(it) }
        val regex = Regex(".*\\.($extRegex)$", RegexOption.IGNORE_CASE)
        val visited = hashSetOf<String>()

        while (dirs.isNotEmpty()) {
            val dir = dirs.removeFirst()
            val files = dir.listFiles() ?: continue
            for (file in files) {
                val absPath = file.absolutePath
                if (visited.contains(absPath)) continue
                visited.add(absPath)

                if (file.isDirectory) {
                    if (!file.name.startsWith(".") && file.name != "cache") {
                        dirs.add(file)
                    }
                } else if (file.name.matches(regex)) {
                    val track = extractTrackFromFile(file)
                    if (track != null) tracks.add(track)
                }
            }
        }
        return tracks
    }

    private fun extractTrackFromFile(file: File): Track? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val id = UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).mostSignificantBits
            val track = Track(
                id = id,
                title = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_TITLE
                )?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                artist = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST
                )?.takeIf { it.isNotBlank() } ?: "未知艺术家",
                album = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM
                )?.takeIf { it.isNotBlank() } ?: "未知专辑",
                duration = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                uri = "file://${file.absolutePath}",
                albumId = 0L
            )
            retriever.release()
            track
        } catch (_: Exception) {
            null
        }
    }
}
