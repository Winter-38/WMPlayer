package com.winter.muplayer.model

data class PlayerStateData(
    val state: PlayerState = PlayerState.IDLE,
    val currentTrack: Track? = null,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val error: Throwable? = null
)