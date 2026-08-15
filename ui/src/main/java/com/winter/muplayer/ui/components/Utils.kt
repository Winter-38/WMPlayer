package com.winter.muplayer.ui.components

import android.content.Context
import com.winter.muplayer.model.Track
import java.io.File

/** ms → "MM:SS" */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** 获取专辑封面本地缓存路径 */
fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>): String? {
    return coverCache[track.id] ?: run {
        if (track.albumId > 0L) {
            "content://media/external/audio/albumart/${track.albumId}"
        } else null
    }
}

/**
 * 后台封面缓存 — 从歌曲文件提取原始内嵌封面（embeddedPicture），
 * 原始编码字节直接写盘，不做任何解码 / 缩放 / 重压缩，保持无损。
 * 未缓存到（无内嵌封面或读取失败）的歌曲，上层回退到 MediaStore
 * 缩略图（有损，仅作临时显示）。
 */
suspend fun cacheCoverFiles(context: Context, tracks: List<Track>, coverCache: MutableMap<Long, String>) {
    // 独立目录（album_covers_hi）：避免命中旧版有损缓存（200px JPEG）
    val cacheDir = File(context.cacheDir, "album_covers_hi")
    cacheDir.mkdirs()
    for (track in tracks) {
        val coverFile = File(cacheDir, "${track.id}.img")
        if (coverFile.exists()) {
            coverCache[track.id] = coverFile.absolutePath
            continue
        }
        try {
            val uri = android.net.Uri.parse(track.uri)
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val picture = retriever.embeddedPicture ?: continue
                coverFile.writeBytes(picture)
                coverCache[track.id] = coverFile.absolutePath
            } finally {
                retriever.release()
            }
        } catch (_: Exception) { }
    }
}
