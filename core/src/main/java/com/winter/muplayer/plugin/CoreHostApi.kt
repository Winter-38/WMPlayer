package com.winter.muplayer.plugin

import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.Track
import com.winter.muplayer.plugin_api.HostApi
import kotlinx.coroutines.flow.StateFlow

/**
 * HostApi 的实现，将插件请求委托给 MusicPlayerCore。
 * 仅暴露安全的操作子集，不暴露 release、stop、clearQueue 等危险方法。
 */
internal class CoreHostApi(
    private val core: MusicPlayerCore
) : HostApi {

    override val playerState: StateFlow<PlayerStateData>
        get() = core.playerState

    override val queue: StateFlow<List<Track>>
        get() = core.queueManager.queue

    override val currentIndex: StateFlow<Int>
        get() = core.queueManager.currentIndex

    override fun play() {
        core.play()
    }

    override fun pause() {
        core.pause()
    }

    override fun playNext() {
        core.playNext()
    }

    override fun playPrevious() {
        core.playPrevious()
    }

    override fun playTrackAtIndex(index: Int) {
        core.playTrackAtIndex(index)
    }

    override fun seekTo(positionMs: Long) {
        core.seekTo(positionMs)
    }

    override fun getCurrentTrack(): Track? {
        return core.playerState.value.currentTrack
    }

    override fun addTrack(track: Track) {
        core.addTrack(track)
    }

    override fun addTracks(tracks: List<Track>) {
        core.addTracks(tracks)
    }

    override fun removeTrack(index: Int) {
        core.removeTrack(index)
    }

    override fun getPlayMode(): PlayMode {
        return core.getPlayModeSync()
    }
}