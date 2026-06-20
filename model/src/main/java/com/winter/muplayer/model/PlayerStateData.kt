package com.winter.muplayer.model

/**
 * 播放器的完整状态快照～
 * 每次状态变化都会打包成这个数据结构发出去，
 * UI 层拿到它就知道该显示什么了。
 *
 * @param state 播放器当前处于啥状态？IDLE / LOADING / PLAYING / PAUSED / ERROR
 * @param currentTrack 现在正在播的是哪首歌？没有就是 null
 * @param progress 播到第几毫秒了
 * @param duration 这首歌总共多长（毫秒）
 * @param error 如果出错了，这里会塞一个异常对象告诉你为啥
 */
data class PlayerStateData(
    val state: PlayerState = PlayerState.IDLE,
    val currentTrack: Track? = null,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val error: Throwable? = null
)
