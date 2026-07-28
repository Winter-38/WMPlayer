package com.winter.muplayer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import coil.ImageLoader
import coil.request.ImageRequest
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

/** 根据封面亮度计算自适应文字/图标颜色 */
fun computeAdaptiveTint(bitmap: Bitmap): androidx.compose.ui.graphics.Color {
    var r = 0f; var g = 0f; var b = 0f; var count = 0
    val step = maxOf(1, bitmap.width / 8)
    for (x in 0 until bitmap.width step step) {
        for (y in 0 until bitmap.height step step) {
            val pixel = bitmap.getPixel(x, y)
            r += Color.red(pixel); g += Color.green(pixel); b += Color.blue(pixel)
            count++
        }
    }
    if (count > 0) { r /= count; g /= count; b /= count }
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    return if (luminance > 0.5) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.87f)
    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.87f)
}

/** 获取专辑封面本地缓存 URI */
fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>): String? {
    return coverCache[track.id] ?: run {
        if (track.albumId > 0L) {
            "content://media/external/audio/albumart/${track.albumId}"
        } else null
    }
}

/** 后台封面缓存（图片加载 + 磁盘写入） */
suspend fun cacheCoverFiles(context: Context, tracks: List<Track>, coverCache: MutableMap<Long, String>) {
    val cacheDir = File(context.cacheDir, "album_covers")
    cacheDir.mkdirs()
    for (track in tracks) {
        val coverFile = File(cacheDir, "${track.id}.jpg")
        if (coverFile.exists()) {
            coverCache[track.id] = coverFile.absolutePath
            continue
        }
        try {
            if (track.albumId > 0L) {
                val uri = "content://media/external/audio/albumart/${track.albumId}"
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(200, 200)
                    .crossfade(false)
                    .build()
                val result = loader.execute(request)
                val drawable = result.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    cacheDir.mkdirs()
                    val fos = java.io.FileOutputStream(coverFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                    fos.close()
                    coverCache[track.id] = coverFile.absolutePath
                }
            }
        } catch (_: Exception) { }
    }
}

/** 计算封面缓存目录大小 */
fun computeCoverCacheSize(context: Context): String {
    val cacheDir = File(context.cacheDir, "album_covers")
    if (!cacheDir.isDirectory) return "0 KB"
    val total = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    return when {
        total < 1024 -> "${total} B"
        total < 1024 * 1024 -> "${total / 1024} KB"
        else -> "${total / (1024 * 1024)} MB"
    }
}
