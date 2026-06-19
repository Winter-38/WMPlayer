package com.winter.muplayer.core

import android.content.Context
import android.os.Bundle
import com.winter.muplayer.engine.ExoPlayerEngine
import com.winter.muplayer.engine.PlayerEngine
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import com.winter.muplayer.plugin.PluginHost
import com.winter.muplayer.plugin_api.PluginContract
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

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

    private val pluginHost = PluginHost(context) { action, params ->
        scope.launch {
            when (action) {
                PluginContract.ACTION_PLAY -> play()
                PluginContract.ACTION_PAUSE -> pause()
                PluginContract.ACTION_NEXT -> playNext()
                PluginContract.ACTION_PREV -> playPrevious()
                PluginContract.ACTION_STOP -> stop()
                PluginContract.ACTION_SEEK ->
                    seekTo(params.getLong(PluginContract.KEY_SEEK_POSITION, 0L))
            }
        }
    }

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

        // 插件系统：扫描并加载
        pluginHost.loadAll()

        // 监听播放器状态变化，推送给插件
        scope.launch {
            var lastTrackId = -1L
            _playerState.collect { state ->
                // 推送状态变化
                pluginHost.broadcast(
                    PluginContract.METHOD_ON_STATE_CHANGE,
                    playerStateToBundle(state)
                )
                // 如果曲目切换了，单独推送 track_change
                val currentId = state.currentTrack?.id ?: -1L
                if (currentId != lastTrackId) {
                    lastTrackId = currentId
                    state.currentTrack?.let { track ->
                        pluginHost.broadcast(
                            PluginContract.METHOD_ON_TRACK_CHANGE,
                            trackToBundle(track)
                        )
                    }
                }
            }
        }

        // 监听播放模式变化
        scope.launch {
            _playMode.collect { mode ->
                pluginHost.broadcast(
                    PluginContract.METHOD_ON_PLAY_MODE_CHANGE,
                    Bundle().apply { putString(PluginContract.KEY_PLAY_MODE, mode.name) }
                )
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
        if (isReleased) return
        scope.launch {
            val currentTrack = queueManager.getCurrentTrack() ?: return@launch
            if (!engine.isReady()) {
                prepareTrack(currentTrack)
            }
            engine.play()
        }
    }

    fun playTrackAtIndex(index: Int) {
        if (isReleased) return
        scope.launch {
            queueManager.setCurrentIndex(index)
            val track = queueManager.getCurrentTrack()
            if (track != null) {
                prepareTrack(track)
                engine.play()
            }
        }
    }

    fun pause() {
        if (isReleased) return
        scope.launch {
            engine.pause()
        }
    }

    fun stop() {
        if (isReleased) return
        scope.launch {
            progressTracker.stop()
            engine.stop()
        }
    }

    fun playNext() {
        if (isReleased) return
        scope.launch {
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
        if (isReleased) return
        scope.launch {
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
        if (isReleased) return
        scope.launch {
            engine.seekTo(positionMs)
        }
    }

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    fun setPlayMode(mode: PlayMode) {
        scope.launch {
            if (isReleased) return@launch
            queueManager.setPlayMode(mode)
            _playMode.value = mode
        }
    }

    fun addTrack(track: Track) {
        if (isReleased) return
        scope.launch {
            queueManager.addTrack(track)
        }
    }

    fun addTracks(tracks: List<Track>) {
        if (isReleased) return
        scope.launch {
            queueManager.addTracks(tracks)
        }
    }

    fun removeTrack(index: Int) {
        if (isReleased) return
        scope.launch {
            queueManager.removeTrack(index)
        }
    }

    fun clearQueue() {
        if (isReleased) return
        scope.launch {
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
        pluginHost.unloadAll()
        engine.release()
        scope.cancel()
        synchronized(Companion) {
            instance = null
        }
    }

    // ==================== 插件事件序列化 ====================

    private fun playerStateToBundle(state: PlayerStateData): Bundle {
        val json = JSONObject()
        json.put("state", state.state.name)
        json.put("progress", state.progress)
        json.put("duration", state.duration)
        state.currentTrack?.let { track ->
            json.put("track", trackToJson(track))
        }
        return Bundle().apply {
            putString(PluginContract.KEY_STATE_JSON, json.toString())
        }
    }

    private fun trackToBundle(track: Track): Bundle {
        return Bundle().apply {
            putString(PluginContract.KEY_TRACK_JSON, trackToJson(track).toString())
        }
    }

    private fun trackToJson(track: Track): JSONObject {
        return JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("duration", track.duration)
            put("uri", track.uri)
            put("albumId", track.albumId)
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