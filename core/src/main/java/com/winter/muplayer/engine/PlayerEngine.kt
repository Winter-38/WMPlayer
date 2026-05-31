package com.winter.muplayer.engine

import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlayerEngine {
    val playerState: StateFlow<PlayerStateData>

    fun prepare(track: Track)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun release()

    fun setOnTrackEndListener(listener: () -> Unit)

    fun getCurrentPosition(): Long

    fun getDuration(): Long

    fun isReady(): Boolean
}

