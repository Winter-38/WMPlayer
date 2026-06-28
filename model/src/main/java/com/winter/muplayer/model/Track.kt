package com.winter.muplayer.model

/**
 * 一首歌的数据结构～
 * 从本地扫出来的每一首歌都会变成这个模样，
 * 包含了歌曲名、歌手、专辑这些基本信息。
 *
 * @param id 歌曲的唯一 ID（MediaStore 里那个 ID，或者文件系统模式下用文件名算出来的 UUID）
 * @param title 歌名！如果文件没有标签信息就拿文件名凑合一下
 * @param artist 歌手名，找不到的话就显示"未知艺术家"啦
 * @param album 专辑名，同理找不到就是"未知专辑"
 * @param duration 时长，单位毫秒
 * @param uri 播放地址，可能是 content:// 也可能是 file://
 * @param albumId 专辑 ID，可以用来找封面图，0 表示没有
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val albumId: Long = 0L,
    val fileSize: Long = 0L,
    val dateAdded: Long = 0L
) {
    val fileType: String
        get() {
            val ext = uri.substringAfterLast('.', "").takeIf { it.length <= 5 } ?: ""
            return when (ext.lowercase()) {
                "mp3" -> "MP3"
                "flac" -> "FLAC"
                "wav" -> "WAV"
                "m4a", "aac" -> "AAC"
                "ogg" -> "OGG"
                "wma" -> "WMA"
                "ape" -> "APE"
                else -> ext.uppercase().ifEmpty { "音频" }
            }
        }
}
