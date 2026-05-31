package com.winter.muplayer.core

import android.content.Context
import com.winter.muplayer.engine.ExoPlayerEngine
import com.winter.muplayer.engine.PlayerEngine
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MusicPlayerCore private constructor(context: Context) : AutoCloseable {

    companion object {
        @Volatile
        private var instance: MusicPlayerCore? = null

        fun getInstance(context: Context): MusicPlayerCore {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerCore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isReleased = false

    val queueManager = PlayQueueManager()

    private val engine: PlayerEngine = ExoPlayerEngine(context)

    private val progressTracker = ProgressTracker(scope, engine)

    private val _playerState = MutableStateFlow(PlayerStateData())
    val playerState: StateFlow<PlayerStateData> = _playerState.asStateFlow()

    init {
        engine.setOnTrackEndListener {
            scope.launch {
                handleTrackCompletion()
            }
        }

        scope.launch {
            engine.playerState.collect { engineState ->
                _playerState.update {
                    it.copy(
                        state = engineState.state,
                        error = engineState.error
                    )
                }
            }
        }

        scope.launch {
            progressTracker.progressState.collect { progressData ->
                _playerState.update {
                    it.copy(
                        progress = progressData.progress,
                        duration = progressData.duration
                    )
                }
            }
        }
    }

    fun prepareTrack(track: Track) {
        scope.launch {
            if (isReleased) return@launch
            _playerState.update {
                it.copy(
                    currentTrack = track,
                    progress = 0L,
                    duration = track.duration
                )
            }
            engine.prepare(track)
            progressTracker.start()
        }
    }

    fun play() {
        scope.launch {
            if (isReleased) return@launch
            val currentTrack = queueManager.getCurrentTrack()
            if (currentTrack != null && !engine.isReady()) {
                prepareTrack(currentTrack)
            }
            engine.play()
            progressTracker.start()
        }
    }

    fun playTrackAtIndex(index: Int) {
        scope.launch {
            if (isReleased) return@launch
            queueManager.setCurrentIndex(index)
            val track = queueManager.getCurrentTrack()
            if (track != null) {
                prepareTrack(track)
                engine.play()
            }
        }
    }

    fun pause() {
        scope.launch {
            if (isReleased) return@launch
            engine.pause()
        }
    }

    fun stop() {
        scope.launch {
            if (isReleased) return@launch
            progressTracker.stop()
            engine.stop()
        }
    }

    fun playNext() {
        scope.launch {
            if (isReleased) return@launch
            progressTracker.stop()
            val nextTrack = queueManager.getNextTrack()
            if (nextTrack != null) {
                prepareTrack(nextTrack)
                engine.play()
            } else {
                engine.stop()
                _playerState.update {
                    it.copy(
                        state = PlayerState.IDLE,
                        progress = 0L
                    )
                }
            }
        }
    }

    fun playPrevious() {
        scope.launch {
            if (isReleased) return@launch
            if (queueManager.isEmpty()) return@launch

            progressTracker.stop()
            val prevTrack = queueManager.getPreviousTrack()
            if (prevTrack != null) {
                prepareTrack(prevTrack)
                engine.play()
            } else {
                engine.seekTo(0L)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            if (isReleased) return@launch
            engine.seekTo(positionMs)
        }
    }

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode       // 立即更新状态流
        scope.launch {
            queueManager.setPlayMode(mode)
        }
    }

    fun getPlayModeSync(): PlayMode = _playMode.value

    fun addTrack(track: Track) {
        scope.launch {
            if (isReleased) return@launch
            queueManager.addTrack(track)
        }
    }

    fun addTracks(tracks: List<Track>) {
        scope.launch {
            if (isReleased) return@launch
            queueManager.addTracks(tracks)
        }
    }

    fun removeTrack(index: Int) {
        scope.launch {
            if (isReleased) return@launch
            queueManager.removeTrack(index)
        }
    }

    fun clearQueue() {
        scope.launch {
            if (isReleased) return@launch
            progressTracker.stop()
            engine.stop()
            queueManager.clear()
            _playerState.update {
                PlayerStateData()
            }
        }
    }

    override fun close() {
        release()
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        progressTracker.stop()
        engine.release()
        scope.cancel()
        synchronized(Companion) {
            instance = null
        }
    }

    private suspend fun handleTrackCompletion() {
        when (queueManager.getPlayMode()) {
            PlayMode.SINGLE_LOOP -> {
                val track = queueManager.getCurrentTrack()
                if (track != null) {
                    _playerState.update {
                        it.copy(state = PlayerState.LOADING)
                    }
                    engine.prepare(track)
                    engine.play()
                } else {
                    _playerState.update {
                        it.copy(
                            state = PlayerState.ERROR,
                            error = IllegalStateException("No track to repeat in SINGLE_LOOP mode")
                        )
                    }
                }
            }
            else -> {
                val nextTrack = queueManager.getNextTrack()
                if (nextTrack != null) {
                    _playerState.update {
                        it.copy(state = PlayerState.LOADING)
                    }
                    engine.prepare(nextTrack)
                    engine.play()
                } else {
                    progressTracker.stop()
                    _playerState.update {
                        it.copy(
                            state = PlayerState.IDLE,
                            progress = 0L
                        )
                    }
                }
            }
        }
    }


}