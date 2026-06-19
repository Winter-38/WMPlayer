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
 * 本地音乐扫描器 —— 轻量、双策略、可扩展。
 *
 * **策略选择：**
 * - API 29+（Android 10+）：[MediaStore] 查询，原生索引，速度快，返回 content:// URI
 * - API 28−（Android 9 以下）：[FileSystem] 遍历，扫描多个标准目录
 *
 * **可扩展点：**
 * - `audioExtensions`：支持的音频扩展名集合，可外部传入
 * - 后续可添加 `NetworkScanner`、`BluetoothScanner` 等新策略
 */
class LocalMusicScanner(
    private val context: Context,
    val audioExtensions: Set<String> = DEFAULT_AUDIO_EXTENSIONS
) {
    companion object {
        val DEFAULT_AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "m4a", "ogg", "aac", "wma", "ape", "aiff", "opus"
        )
    }

    /** 内存缓存：扫过一次后不再重复全量扫描 */
    private var cachedTracks: List<Track>? = null
    private var cacheValid = false

    /** 文件系统模式下额外扫描的目录（相对于外部存储根） */
    private val fileSystemScanDirs = listOf(
        "Music", "Download", "Podcasts", "Ringtones",
        "Alarms", "Notifications", "Sounds", "Recordings"
    )

    // ==================== 公开 API ====================

    /** 执行扫描（返回前会写内存缓存） */
    fun scan(): List<Track> {
        if (cacheValid && cachedTracks != null) return cachedTracks!!

        val tracks = if (Build.VERSION.SDK_INT >= 29) {
            scanMediaStore()
        } else {
            scanFileSystem()
        }

        cachedTracks = tracks
        cacheValid = true
        return tracks
    }

    /** 清除内存缓存，下次 [scan] 会重新扫描 */
    fun invalidateCache() {
        cacheValid = false
    }

    // ==================== MediaStore 策略 (API 29+) ====================

    private fun scanMediaStore(): List<Track> {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE
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

            while (cursor.moveToNext()) {
                val data = cursor.getString(colData) ?: continue
                val id = cursor.getLong(colId)
                val title = cursor.getString(colTitle)?.takeIf { it.isNotBlank() }
                    ?: File(data).nameWithoutExtension
                val artist = cursor.getString(colArtist)?.takeIf { it.isNotBlank() }
                    ?: "未知艺术家"
                val album = cursor.getString(colAlbum)?.takeIf { it.isNotBlank() }
                    ?: "未知专辑"
                val duration = cursor.getLong(colDuration).coerceAtLeast(0L)

                // API 30+ 使用 content URI，可跨应用访问
                // API 29 回退到 file:// URI
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
                        albumId = albumId
                    )
                )
            }
        }
        return tracks
    }

    // ==================== 文件系统策略 (API 28-) ====================

    private fun scanFileSystem(): List<Track> {
        val tracks = mutableListOf<Track>()
        val extStorage = Environment.getExternalStorageDirectory()
        val dirs = ArrayDeque<File>()

        // 扫描多个标准目录
        for (subDir in fileSystemScanDirs) {
            val dir = File(extStorage, subDir)
            if (dir.isDirectory) dirs.add(dir)
        }

        // 也检查外部 SD 卡（如果有）
        val externalDirs = context.getExternalFilesDirs(null)
        for (extDir in externalDirs) {
            if (extDir != null && extDir.absolutePath.contains("Android", ignoreCase = false)) continue
            val parent = extDir?.parentFile
            if (parent != null && parent.isDirectory && !dirs.contains(parent)) {
                // 对于外部 SD 卡，仅扫描标准的音乐/下载目录
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
                    // 跳过隐藏目录和 Android 缓存
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
            null // 跳过无法读取的文件
        }
    }
}
