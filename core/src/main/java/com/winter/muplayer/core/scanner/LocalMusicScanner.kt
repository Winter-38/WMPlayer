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
 * 根据不同版本的 Android 用不同的策略：
 * - Android 10 以上（API 29+）：用 [MediaStore] 查，又快又省事，返回 content:// URI
 * - Android 9 以下（API 28-）：老老实实遍历文件系统，扫几个标准的音乐目录
 *
 * 支持的音频格式写在 [audioExtensions] 里，你也可以自己传进来扩展～
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

    /** 扫描结果缓存，扫过一次就不用再扫啦（除非主动清缓存） */
    private var cachedTracks: List<Track>? = null
    private var cacheValid = false

    /** 文件系统模式下要去哪些目录翻歌呢？就这几个标准文件夹啦 */
    private val fileSystemScanDirs = listOf(
        "Music", "Download", "Podcasts", "Ringtones",
        "Alarms", "Notifications", "Sounds", "Recordings"
    )

    // ==================== 公开 API：外面就调这俩 ====================

    /** 开始扫描！扫完会存到缓存里，下次直接返回缓存结果不用再扫 */
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

    /** 清掉缓存，下次 [scan] 就会重新扫描一遍～ */
    fun invalidateCache() {
        cacheValid = false
    }

    // ==================== MediaStore 策略（Android 10 以上，又快又好） ====================

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

                // 高版本用 content:// URI 其他应用也能访问，低版本只能退回到 file://
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

    // ==================== 文件系统策略（老安卓的备选方案） ====================

    private fun scanFileSystem(): List<Track> {
        val tracks = mutableListOf<Track>()
        val extStorage = Environment.getExternalStorageDirectory()
        val dirs = ArrayDeque<File>()

        // 先扫几个标准目录
        for (subDir in fileSystemScanDirs) {
            val dir = File(extStorage, subDir)
            if (dir.isDirectory) dirs.add(dir)
        }

        // 如果有 SD 卡也去看看
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

        // 一层层翻目录，像翻抽屉一样找歌～
        while (dirs.isNotEmpty()) {
            val dir = dirs.removeFirst()
            val files = dir.listFiles() ?: continue
            for (file in files) {
                val absPath = file.absolutePath
                if (visited.contains(absPath)) continue
                visited.add(absPath)

                if (file.isDirectory) {
                    // 隐藏目录和缓存目录跳过，里面不会有歌的啦
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

    /**
     * 从一个音频文件里提取曲目信息～
     * 用 MediaMetadataRetriever 读取标签，没有标签就用文件名凑合。
     * 读不了的就跳过，不影响其他文件。
     */
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
            null // 这个文件读不了，跳过吧～
        }
    }
}
