package com.winter.muplayer.engine

import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放器引擎的「接口契约」～
 * 不管是 ExoPlayer 还是别的什么播放器，
 * 只要实现了这个接口，就能被咱家的 MusicPlayerCore 管起来！
 */
interface PlayerEngine {
    /** 播放器的当前状态，随时可以拿来瞅一眼～ */
    val playerState: StateFlow<PlayerStateData>

    /** 准备一首歌：告诉引擎要播啥，让它先加载好 */
    fun prepare(track: Track)

    /** 播放！音乐走起～ */
    fun play()

    /** 暂停一下下，先别放啦 */
    fun pause()

    /** 完全停下来，回到初始状态 */
    fun stop()

    /** 跳到指定的毫秒位置 */
    fun seekTo(positionMs: Long)

    /** 播放器用完啦，释放资源～ */
    fun release()

    /** 设置一个回调，当前曲目播完的时候会调它 */
    fun setOnTrackEndListener(listener: () -> Unit)

    /** 现在播到第几毫秒了？ */
    fun getCurrentPosition(): Long

    /** 这首歌总共多长？ */
    fun getDuration(): Long

    /** 准备好了没？可以播放了吗？ */
    fun isReady(): Boolean
}
