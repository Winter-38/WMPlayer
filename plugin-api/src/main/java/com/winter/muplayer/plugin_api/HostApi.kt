package com.winter.muplayer.plugin_api

import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * 宿主提供给插件的能力接口。
 * 所有方法均为同步调用（内部处理线程调度），状态通过 StateFlow 暴露。
 * 插件应仅依赖此接口与宿主交互，避免直接引用 MusicPlayerCore。
 */
interface HostApi {
    /** 当前播放状态（IDLE/LOADING/PLAYING/PAUSED/ERROR）及进度、当前曲目等 */
    val playerState: StateFlow<PlayerStateData>

    /** 播放队列列表 */
    val queue: StateFlow<List<Track>>

    /** 当前播放索引 */
    val currentIndex: StateFlow<Int>

    /** 请求播放（若未准备好则自动准备当前曲目） */
    fun play()

    /** 暂停播放 */
    fun pause()

    /** 播放下一首 */
    fun playNext()

    /** 播放上一首 */
    fun playPrevious()

    /** 播放队列中指定索引的曲目 */
    fun playTrackAtIndex(index: Int)

    /** 跳转到指定位置（毫秒） */
    fun seekTo(positionMs: Long)

    /** 获取当前曲目，若无则返回 null */
    fun getCurrentTrack(): Track?

    /** 向队列末尾添加一首曲目 */
    fun addTrack(track: Track)

    /** 向队列末尾批量添加曲目 */
    fun addTracks(tracks: List<Track>)

    /** 移除队列中指定索引的曲目 */
    fun removeTrack(index: Int)

    /** 获取当前播放模式 */
    fun getPlayMode(): PlayMode
}

