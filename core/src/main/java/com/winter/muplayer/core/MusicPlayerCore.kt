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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * 播放器的「大脑」—— 所有核心逻辑都在这儿啦！
 *
 * 它是一个单例（全局只有一个），负责：
 * - 管理播放引擎（ExoPlayer）
 * - 管着播放队列和播放列表
 * - 跟插件系统通信
 * - 把播放器状态暴露给 UI 层
 *
 * 用完了记得调 [close] 或 [release] 释放资源哦，不然会漏的！
 */
class MusicPlayerCore private constructor(context: Context) : AutoCloseable {

    companion object {
        @Volatile
        private var instance: MusicPlayerCore? = null

        /** 获取唯一的播放器实例～第一次调会创建，后面都是复用 */
        fun getInstance(context: Context): MusicPlayerCore {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerCore(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /** 所有协程都跑在这个作用域下，用 SupervisorJob 确保一个挂了不影响其他的 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isReleased = false
    /** 保护 engine 操作的互斥锁，防止多个协程同时操作播放器导致状态混乱 */
    private val engineMutex = Mutex()

    /** 播放队列管理器～管着当前要播什么 */
    val queueManager = PlayQueueManager()

    /** 播放列表管理器～管着用户创建的那些歌单 */
    val playlistManager = PlaylistManager(context)

    /** 真正的播放引擎，干活的是它 */
    private val engine: PlayerEngine = ExoPlayerEngine(context)

    /** 进度追踪器，每隔一会儿就问引擎「播到哪了」 */
    private val progressTracker = ProgressTracker(scope, engine)

    /** 插件宿主，跟外部插件聊天就靠它了 */
    val pluginHost = PluginHost(context) { action, params ->
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
        // 监听歌曲播完事件，自动切到下一首
        engine.setOnTrackEndListener {
            scope.launch {
                handleTrackCompletion()
            }
        }

        // 监听引擎状态变化，同步到自己的状态里
        scope.launch {
            engine.playerState.collect { engineState ->
                _playerState.update {
                    it.copy(
                        state = engineState.state,
                        error = engineState.error,
                        currentTrack = engineState.currentTrack
                    )
                }
            }
        }

        // 监听进度变化，也同步到状态里
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

        // 启动时扫描并加载已安装的插件
        pluginHost.loadAll()

        // 播放器状态有变化就推送给所有插件
        scope.launch {
            var lastTrackId = -1L
            _playerState.collect { state ->
                pluginHost.broadcast(
                    PluginContract.METHOD_ON_STATE_CHANGE,
                    playerStateToBundle(state)
                )
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

        // 播放模式变了也告诉插件一声
        scope.launch {
            _playMode.collect { mode ->
                pluginHost.broadcast(
                    PluginContract.METHOD_ON_PLAY_MODE_CHANGE,
                    Bundle().apply { putString(PluginContract.KEY_PLAY_MODE, mode.name) }
                )
            }
        }
    }

    /**
     * 内部准备曲目——加载歌曲数据、重置进度、开始追踪播放进度
     */
    private suspend fun prepareTrackInternal(track: Track) {
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

    /** 准备一首歌，为播放做好准备～ */
    fun prepareTrack(track: Track) {
        scope.launch {
            if (isReleased) return@launch
            engineMutex.withLock {
                prepareTrackInternal(track)
            }
        }
    }

    /** 播放！如果还没准备好会自动先准备 */
    fun play() {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                val currentTrack = queueManager.getCurrentTrack() ?: return@withLock
                if (!engine.isReady()) {
                    prepareTrackInternal(currentTrack)
                }
                engine.play()
            }
        }
    }

    /** 直接播队列里指定位置的歌～ */
    fun playTrackAtIndex(index: Int) {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                queueManager.setCurrentIndex(index)
                val track = queueManager.getCurrentTrack()
                if (track != null) {
                    prepareTrackInternal(track)
                    engine.play()
                }
            }
        }
    }

    /** 暂停～先歇一会儿 */
    fun pause() {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                engine.pause()
            }
        }
    }

    /** 完全停下来 */
    fun stop() {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                progressTracker.stop()
                engine.stop()
            }
        }
    }

    /** 下一首～会根据当前的播放模式算出下一首是什么 */
    fun playNext() {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                progressTracker.stop()
                val nextTrack = queueManager.getNextTrack()
                if (nextTrack != null) {
                    prepareTrackInternal(nextTrack)
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
    }

    /** 上一首～逻辑和下一首差不多 */
    fun playPrevious() {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                if (queueManager.isEmpty()) return@withLock

                progressTracker.stop()
                val prevTrack = queueManager.getPreviousTrack()
                if (prevTrack != null) {
                    prepareTrackInternal(prevTrack)
                    engine.play()
                } else {
                    engine.seekTo(0L)
                }
            }
        }
    }

    /** 跳到指定的毫秒位置～ */
    fun seekTo(positionMs: Long) {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                engine.seekTo(positionMs)
            }
        }
    }

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    /** 切换播放模式～会同步更新队列管理器的模式 */
    fun setPlayMode(mode: PlayMode) {
        scope.launch {
            if (isReleased) return@launch
            queueManager.setPlayMode(mode)
            _playMode.value = mode
        }
    }

    /** 往播放队列里加一首歌～ */
    fun addTrack(track: Track) {
        if (isReleased) return
        scope.launch {
            queueManager.addTrack(track)
        }
    }

    /** 一次加一堆歌～ */
    fun addTracks(tracks: List<Track>) {
        if (isReleased) return
        scope.launch {
            queueManager.addTracks(tracks)
        }
    }

    /** 从队列里移除指定位置的歌 */
    fun removeTrack(index: Int) {
        if (isReleased) return
        scope.launch {
            queueManager.removeTrack(index)
        }
    }

    /** 清空整个播放队列 */
    fun clearQueue() {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                progressTracker.stop()
                engine.stop()
                queueManager.clear()
                _playerState.update {
                    PlayerStateData()
                }
            }
        }
    }

    override fun close() {
        release()
    }

    /** 释放所有资源，播放器不再使用了就调这个～ */
    fun release() {
        if (isReleased) return
        isReleased = true
        runBlocking {
            engineMutex.withLock {
                progressTracker.stop()
                pluginHost.unloadAll()
                engine.release()
            }
        }
        scope.cancel()
        synchronized(Companion) {
            instance = null
        }
    }

    // ==================== 插件事件序列化 ====================

    /** 把播放器状态打包成 Bundle，插件那边看得懂 */
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

    /** 歌曲信息打包成 Bundle */
    private fun trackToBundle(track: Track): Bundle {
        return Bundle().apply {
            putString(PluginContract.KEY_TRACK_JSON, trackToJson(track).toString())
        }
    }

    /** 歌曲信息转成 JSON */
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

    /** 一首歌唱完了——根据播放模式决定下一步干啥 */
    private suspend fun handleTrackCompletion() {
        when (queueManager.getPlayMode()) {
            PlayMode.SINGLE_LOOP -> {
                val track = queueManager.getCurrentTrack()
                if (track != null) {
                    prepareTrackInternal(track)
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
                    prepareTrackInternal(nextTrack)
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
