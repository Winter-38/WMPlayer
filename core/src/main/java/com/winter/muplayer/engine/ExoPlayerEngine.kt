package com.winter.muplayer.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@OptIn(UnstableApi::class)
class ExoPlayerEngine(context: Context) : PlayerEngine {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _playerState = MutableStateFlow(PlayerStateData())
    override val playerState: StateFlow<PlayerStateData> = _playerState.asStateFlow()

    private var onTrackEndListener: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    if (_playerState.value.state != PlayerState.LOADING) {
                        _playerState.update { it.copy(state = PlayerState.IDLE) }
                    }
                }
                Player.STATE_BUFFERING -> {
                    _playerState.update { it.copy(state = PlayerState.LOADING) }
                }
                Player.STATE_READY -> {
                    val isPlaying = exoPlayer.playWhenReady
                    val newState = if (isPlaying) PlayerState.PLAYING else PlayerState.PAUSED
                    _playerState.update {
                        it.copy(
                            state = newState,
                            duration = exoPlayer.duration.coerceAtLeast(0L),
                            progress = exoPlayer.currentPosition.coerceAtLeast(0L)
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    _playerState.update {
                        it.copy(progress = exoPlayer.duration.coerceAtLeast(0L))
                    }
                    onTrackEndListener?.invoke()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                val newState = if (playWhenReady) PlayerState.PLAYING else PlayerState.PAUSED
                _playerState.update { it.copy(state = newState) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playerState.update {
                it.copy(
                    state = PlayerState.ERROR,
                    error = error
                )
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    override fun prepare(track: Track) {
        try {
            _playerState.update {
                it.copy(
                    state = PlayerState.LOADING,
                    currentTrack = track,
                    progress = 0L,
                    duration = track.duration,
                    error = null
                )
            }
            val mediaItem = MediaItem.fromUri(track.uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } catch (e: Exception) {
            _playerState.update {
                it.copy(
                    state = PlayerState.ERROR,
                    error = e
                )
            }
        }
    }

    override fun play() {
        exoPlayer.playWhenReady = true
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun stop() {
        exoPlayer.stop()
        _playerState.update {
            it.copy(
                state = PlayerState.IDLE,
                progress = 0L
            )
        }
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playerState.update { it.copy(progress = positionMs) }
    }

    override fun release() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }

    override fun setOnTrackEndListener(listener: () -> Unit) {
        onTrackEndListener = listener
    }

    override fun getCurrentPosition(): Long = exoPlayer.currentPosition.coerceAtLeast(0L)

    override fun getDuration(): Long = exoPlayer.duration.coerceAtLeast(0L)

    override fun isReady(): Boolean = exoPlayer.playbackState == Player.STATE_READY
}
