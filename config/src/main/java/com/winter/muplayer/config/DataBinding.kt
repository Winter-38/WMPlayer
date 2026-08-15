package com.winter.muplayer.config

import com.winter.muplayer.core.ProgressTracker

/**
 * 数据绑定 —— 通用组件（text 等）通过 `extra["bind"]` 引用播放器运行时状态，
 * 无需为每种数据写一个专用组件。
 *
 * 支持的 bind 键：
 * - `track.title`     当前歌曲标题
 * - `track.artist`    当前歌曲歌手
 * - `track.album`     当前歌曲专辑
 * - `track.artistAlbum` "歌手 • 专辑" 组合文本
 * - `track.duration`  当前歌曲总时长（MM:SS）
 * - `position`        当前播放位置（MM:SS）
 * - `duration`        播放队列总时长（MM:SS，来自进度追踪器）
 *
 * 未命中时返回 null，调用方自行决定回退文案。
 */
object DataBinding {

    fun resolve(key: String, context: SlotContext, progress: ProgressTracker.ProgressData): String? {
        val track = context.playerState.currentTrack
        return when (key) {
            "track.title", "title" -> track?.title
            "track.artist", "artist" -> track?.artist
            "track.album", "album" -> track?.album
            "track.artistAlbum" -> track?.let { "${it.artist} • ${it.album}" }
            "track.duration" -> track?.let { formatDurationMs(it.duration) }
            "position" -> formatDurationMs(progress.progress)
            "duration" -> formatDurationMs(progress.duration)
            else -> null
        }
    }

    /** ms → "M:SS"（0 或负数 → "0:00"，与 UI 层 formatDuration 的 "--:--" 语义区分，绑定文本用于行内展示） */
    fun formatDurationMs(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
