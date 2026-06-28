package com.winter.muplayer.plugin_runtime

import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * 宿主暴露给插件的播放器控制接口。
 *
 * Shadow 架构中的 Runtime 层 —— 插件通过这个接口
 * 读取播放器状态、控制播放、操作队列，无需通过
 * ContentProvider 做 IPC，直接同进程调用。
 *
 * 实现类在宿主侧 [com.winter.muplayer.core.MusicPlayerCore]。
 */
interface IPlayerHost {

    // ========== 播放控制 ==========

    /** 播放 */
    fun play()

    /** 暂停 */
    fun pause()

    /** 停止 */
    fun stop()

    /** 下一首 */
    fun playNext()

    /** 上一首 */
    fun playPrevious()

    /** 跳到指定毫秒位置 */
    fun seekTo(positionMs: Long)

    /** 切换播放模式 */
    fun setPlayMode(mode: String)  // "SEQUENTIAL" / "SHUFFLE" / "SINGLE_LOOP"

    // ========== 状态查询 ==========

    /** 播放器完整状态（StateFlow，可 collect） */
    val playerState: StateFlow<PlayerStateData>

    /** 当前播放模式 */
    val playMode: StateFlow<PlayMode>

    /** 当前曲目 */
    val currentTrack: Track?

    /** 总时长（毫秒） */
    val duration: Long

    /** 当前进度（毫秒） */
    val progress: Long

    /** 是否正在播放 */
    val isPlaying: Boolean

    // ========== 队列操作 ==========

    /** 播放指定曲目 */
    fun playTrack(track: Track)

    /** 追加曲目到队列末尾 */
    fun addTrack(track: Track)

    /** 批量追加到队列末尾 */
    fun addTracks(tracks: List<Track>)

    /** 获取当前队列 */
    fun getQueue(): List<Track>
}
