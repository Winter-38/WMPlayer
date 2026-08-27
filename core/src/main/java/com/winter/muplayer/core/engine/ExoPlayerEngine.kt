package com.winter.muplayer.core.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@OptIn(UnstableApi::class)
class ExoPlayerEngine(context: Context) : PlayerEngine {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** ExoPlayer 懒加载 - 首次用到时才初始化，避免启动卡顿 */
    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().also {
            it.addListener(playerListener)
        }
    }

    private val _playerState = MutableStateFlow(PlayerStateData())
    override val playerState: StateFlow<PlayerStateData> = _playerState.asStateFlow()

    private var onTrackEndListener: (() -> Unit)? = null
    private var crossfadeMs: Int = 0
    /** 跨fade 用的协程作用域 */
    private val crossfadeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var duckOnFocusLoss: Boolean = false
    private var hasAudioFocus = false
    /** 保存 AudioFocusRequest 实例，release 时传入同一个对象（API 26+ 要求） */
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    /** 在音频焦点临时丢失前是否正在播放，用于焦点恢复后自动续播 */
    private var wasPlayingBeforeFocusLoss = false

    // AudioFocus 回调
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeFocusLoss = false // 永久丢失，不续播
                exoPlayer.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (duckOnFocusLoss) {
                    exoPlayer.volume = 0.3f
                } else {
                    wasPlayingBeforeFocusLoss = exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (duckOnFocusLoss) {
                    exoPlayer.volume = 0.3f
                } else {
                    wasPlayingBeforeFocusLoss = exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                exoPlayer.volume = 1.0f
                if (wasPlayingBeforeFocusLoss) {
                    exoPlayer.play()
                }
                wasPlayingBeforeFocusLoss = false
            }
        }
    }

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
                    _playerState.update { it.copy(state = newState) }
                }
                Player.STATE_ENDED -> {
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

    /** 设置跨fade 时长（毫秒），0=关闭 */
    fun setCrossfadeDuration(ms: Int) {
        crossfadeMs = ms.coerceIn(0, 5000)
    }

    /** 设置音频焦点策略 */
    fun setAudioFocusDuck(duck: Boolean) {
        duckOnFocusLoss = duck
    }

    /** 设置播放音量（0~1）。 */
    fun setVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    /** 设置播放倍速（>=0.05）。 */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed.coerceAtLeast(0.05f))
    }

    /** 请求音频焦点 */
    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val result = if (android.os.Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build().also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** 释放音频焦点 */
    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val savedRequest = audioFocusRequest
            if (savedRequest != null) {
                audioManager.abandonAudioFocusRequest(savedRequest)
            }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    override fun prepare(track: Track) {
        try {
            _playerState.update {
                it.copy(
                    state = PlayerState.LOADING,
                    currentTrack = track,
                    error = null
                )
            }
            val mediaItem = MediaItem.Builder()
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
            val isPlaying = exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_READY
            if (crossfadeMs > 0 && isPlaying) {
                // 手动音量交叉淡入淡出
                val currentVolume = exoPlayer.volume
                crossfadeScope.launch {
                    // 旧曲淡出
                    val fadeOutSteps = (crossfadeMs / 50f).toInt().coerceAtLeast(1)
                    for (i in fadeOutSteps downTo 0) {
                        exoPlayer.volume = currentVolume * i / fadeOutSteps
                        delay(50)
                    }
                    // 切换曲目
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    exoPlayer.volume = 0f
                    // 新曲淡入
                    val fadeInSteps = (crossfadeMs / 50f).toInt().coerceAtLeast(1)
                    for (i in 0..fadeInSteps) {
                        exoPlayer.volume = currentVolume * i / fadeInSteps
                        delay(50)
                    }
                }
            } else {
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            }
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
        requestAudioFocus()
        exoPlayer.playWhenReady = true
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun stop() {
        exoPlayer.stop()
        abandonAudioFocus()
        _playerState.update { it.copy(state = PlayerState.IDLE) }
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun release() {
        exoPlayer.removeListener(playerListener)
        abandonAudioFocus()
        exoPlayer.release()
    }

    override fun setOnTrackEndListener(listener: () -> Unit) {
        onTrackEndListener = listener
    }

    override fun getCurrentPosition(): Long = exoPlayer.currentPosition.coerceAtLeast(0L)

    override fun getDuration(): Long = exoPlayer.duration.coerceAtLeast(0L)

    override fun isReady(): Boolean = exoPlayer.playbackState == Player.STATE_READY
}