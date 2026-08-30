package com.winter.muplayer.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/**
 * 获取专辑封面本地缓存路径。
 *
 * 封面缓存分层：
 * - 原始无损文件 `{id}.img`（embeddedPicture 原始字节）—— 全屏播放器使用（[preferOriginal] = true）；
 * - 压缩缩略图 `{id}.thumb.jpg`（256px JPEG）—— 列表 / 迷你栏等缩略图使用（默认）。
 *
 * 缩略图场景优先返回压缩缩略图（体积小、加载快）；缩略图缺失（旧缓存升级等）时回退原始文件。
 * 未命中缓存时回退 MediaStore albumart content uri（系统缩略图，仅作临时显示）。
 */
fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>, preferOriginal: Boolean = false): String? {
    val cached = coverCache[track.id]
    if (cached != null) {
        if (preferOriginal) return cached
        val thumb = thumbPathOf(cached)
        if (File(thumb).isFile) return thumb
        return cached
    }
    return if (track.albumId > 0L) {
        "content://media/external/audio/albumart/${track.albumId}"
    } else null
}

/** 由原始封面路径推导压缩缩略图路径：`xxx.img` → `xxx.thumb.jpg` */
fun thumbPathOf(originalPath: String): String =
    originalPath.removeSuffix(".img") + ".thumb.jpg"

/**
 * 按需封面缓存 — 单曲：从歌曲文件提取原始内嵌封面（embeddedPicture）。
 *
 * - 原始编码字节写 `album_covers_hi/{id}.img`（无损，供全屏播放器）；
 * - 同时解码压缩出 `album_covers_hi/{id}.thumb.jpg`（256px JPEG，供缩略图）；
 * - 仅在实际使用封面的场景（当前播放、全屏封面等）调用，**不做全库预缓存**；
 * - 已缓存时 touch 原始文件时间戳（LRU 依据），并补生成缺失的缩略图（旧缓存升级）；
 * - 无内嵌封面或读取失败时静默跳过，显示端回退 MediaStore 缩略图；
 * - 每次写入后按容量上限裁剪，只保留近期使用过的少量封面。
 */
suspend fun cacheCoverFile(context: Context, track: Track, coverCache: MutableMap<Long, String>) {
    if (track.id <= 0L) return
    // 独立目录（album_covers_hi）：避免命中旧版有损缓存（200px JPEG）
    val cacheDir = File(context.cacheDir, "album_covers_hi")
    val coverFile = File(cacheDir, "${track.id}.img")
    if (coverFile.exists()) {
        // LRU：使用即刷新时间戳，保证裁剪时保留的是真正在用/刚用过的封面
        coverFile.setLastModified(System.currentTimeMillis())
        coverCache[track.id] = coverFile.absolutePath
        // 旧缓存升级：补生成缺失的缩略图
        val thumb = File(thumbPathOf(coverFile.absolutePath))
        if (!thumb.isFile) {
            try { writeThumb(coverFile.readBytes(), thumb) } catch (_: Exception) { }
        }
        return
    }
    try {
        val uri = android.net.Uri.parse(track.uri)
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture ?: return
            cacheDir.mkdirs()
            coverFile.writeBytes(picture)
            writeThumb(picture, File(thumbPathOf(coverFile.absolutePath)))
            coverCache[track.id] = coverFile.absolutePath
        } finally {
            retriever.release()
        }
    } catch (_: Exception) { }
    trimCoverCache(context)
}

/**
 * 封面缓存 LRU 上限裁剪：只保留最近使用的 [maxCount] 张原始封面（连同其缩略图一起删），
 * 超出删除最久未使用的。需在 IO 线程调用。
 */
fun trimCoverCache(context: Context, maxCount: Int = 40) {
    val cacheDir = File(context.cacheDir, "album_covers_hi")
    val originals = cacheDir.listFiles { f -> f.isFile && f.extension == "img" }?.toList() ?: return
    if (originals.size <= maxCount) return
    originals.sortedBy { it.lastModified() }
        .take(originals.size - maxCount)
        .forEach { original ->
            original.delete()
            File(thumbPathOf(original.absolutePath)).delete()
        }
}

/**
 * 从封面原始字节生成压缩缩略图（最长边 [maxSizePx] 的 JPEG，质量 [quality]）。
 * 解码失败 / 压缩失败时静默跳过（调用方回退原始文件或 MediaStore）。
 */
private fun writeThumb(sourceBytes: ByteArray, thumbFile: File, maxSizePx: Int = 256, quality: Int = 85) {
    try {
        val bmp = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return
        val scale = minOf(1f, maxSizePx.toFloat() / maxOf(bmp.width, bmp.height))
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
        thumbFile.parentFile?.mkdirs()
        thumbFile.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
    } catch (_: Exception) { }
}
