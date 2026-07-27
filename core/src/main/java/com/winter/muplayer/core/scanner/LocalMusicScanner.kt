package com.winter.muplayer.core.scanner

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.winter.muplayer.model.Track
import org.json.JSONArray
import org.json.JSONObject
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

    /** 强制重新扫描（清除磁盘缓存） */
    fun forceRescan(): List<Track> {
        cachedTracks = emptyList()
        cacheLoaded = false
        invalidateDiskCache()
        return scanFull()
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
            val json = JSONObject(file.readText())
            val arr = json.optJSONArray("tracks") ?: JSONArray()
            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Track(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        artist = obj.optString("artist", "未知艺术家"),
                        album = obj.optString("album", "未知专辑"),
                        duration = obj.getLong("duration"),
                        uri = obj.getString("uri"),
                        albumId = obj.optLong("albumId", 0L),
                        fileSize = obj.optLong("fileSize", 0L),
                        dateAdded = obj.optLong("dateAdded", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            // 缓存文件损坏，删除后重新扫描
            file.delete()
            emptyList()
        }
    }

    private fun saveToDisk(tracks: List<Track>) {
        try {
            val arr = JSONArray()
            for (track in tracks) {
                arr.put(
                    JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("duration", track.duration)
                        put("uri", track.uri)
                        put("albumId", track.albumId)
                        put("fileSize", track.fileSize)
                        put("dateAdded", track.dateAdded)
                    }
                )
            }
            getCacheFile().writeText(
                JSONObject().apply {
                    put("version", 1)
                    put("count", tracks.size)
                    put("tracks", arr)
                }.toString()
            )
        } catch (_: Exception) {
            // 写入失败不阻塞主流程，下次扫描时重试
        }
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
