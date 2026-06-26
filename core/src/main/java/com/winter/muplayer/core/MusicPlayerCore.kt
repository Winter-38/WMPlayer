package com.winter.muplayer.core

import android.content.Context
import com.winter.muplayer.engine.ExoPlayerEngine
import com.winter.muplayer.engine.PlayerEngine
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import com.winter.muplayer.plugin.ShadowPluginHost
import com.winter.muplayer.plugin_runtime.IPlayerHost
import com.winter.muplayer.core.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
class MusicPlayerCore private constructor(context: Context) : AutoCloseable, IPlayerHost {

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

    /** 这是播放引擎，干活的是它 */
    private val engine: PlayerEngine = ExoPlayerEngine(context)

    /** 进度追踪器，每隔一会儿就问引擎「播到哪了」 */
    private val progressTracker = ProgressTracker(scope, engine)

    /** Shadow 插件宿主——基于 DexClassLoader 的动态插件加载 */
    val shadowPluginHost = ShadowPluginHost(context, this)

    private val _playerState = MutableStateFlow(PlayerStateData())
    override val playerState: StateFlow<PlayerStateData> = _playerState.asStateFlow()

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

        // 启动时加载所有已安装的插件（Shadow 架构）
        shadowPluginHost.loadAll()

        // Shadow 架构下插件通过 IPlayerHost 直接读取状态，
        // 无需再广播状态变化——插件自己 collect StateFlow 即可。
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
    override fun play() {
        AppLogger.i("Player", "play")
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
    override fun pause() {
        AppLogger.i("Player", "pause")
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                engine.pause()
            }
        }
    }

    /** 完全停下来 */
    override fun stop() {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                progressTracker.stop()
                engine.stop()
            }
        }
    }

    /** 下一首～会根据当前的播放模式算出下一首是什么 */
    override fun playNext() {
        AppLogger.i("Player", "playNext")
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
    override fun playPrevious() {
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
    override fun seekTo(positionMs: Long) {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                engine.seekTo(positionMs)
            }
        }
    }

    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    override val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    /** 切换播放模式～会同步更新队列管理器的模式 */
    fun setPlayMode(mode: PlayMode) {
        scope.launch {
            if (isReleased) return@launch
            queueManager.setPlayMode(mode)
            _playMode.value = mode
        }
    }

    /** 往播放队列里加一首歌～ */
    override fun addTrack(track: Track) {
        if (isReleased) return
        scope.launch {
            queueManager.addTrack(track)
        }
    }

    /**
     * 添加一首歌到队列并立即播放。
     * 即使当前正在播放也会切到这首歌。
     */
    override fun playTrack(track: Track) {
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                queueManager.addTrack(track)
                // 栈插入后新歌在 index=0
                queueManager.setCurrentIndex(0)
                val currentTrack = queueManager.getCurrentTrack()
                if (currentTrack != null) {
                    progressTracker.stop()
                    prepareTrackInternal(currentTrack)
                    engine.play()
                }
            }
        }
    }

    /** 一次加一堆歌～ */
    override fun addTracks(tracks: List<Track>) {
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
                shadowPluginHost.unloadAll()
                engine.release()
            }
        }
        scope.cancel()
        shadowPluginHost.release()
        synchronized(Companion) {
            instance = null
        }
    }

    /**
     * 智能播放：播放一首歌，并根据队列状态决定是否批量添加。
     *
     * - 队列为空 → 将 [batch] 中的所有歌曲加入队列，播放 [track]
     * - 队列非空 → 仅添加 [track] 单曲并播放
     */
    fun playTrackSmart(track: Track, batch: List<Track>) {
        AppLogger.i("Player", "playTrackSmart: ${track.title} (batch=${batch.size})")
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                if (queueManager.isEmpty()) {
                    // 队列为空：先添加所有 batch 歌曲，再定位到目标曲目
                    queueManager.addTracks(batch)
                    val targetIndex = queueManager.queue.value.indexOfFirst { it.track.id == track.id }
                    if (targetIndex >= 0) {
                        queueManager.setCurrentIndex(targetIndex)
                        val currentTrack = queueManager.getCurrentTrack()
                        if (currentTrack != null) {
                            progressTracker.stop()
                            prepareTrackInternal(currentTrack)
                            engine.play()
                        }
                    }
                } else {
                    // 队列非空：只加单曲切到该曲
                    queueManager.addTrack(track)
                    queueManager.setCurrentIndex(0)
                    val currentTrack = queueManager.getCurrentTrack()
                    if (currentTrack != null) {
                        progressTracker.stop()
                        prepareTrackInternal(currentTrack)
                        engine.play()
                    }
                }
            }
        }
    }

    // ==================== IPlayerHost 桥接 ====================

    /** 切换播放模式（String → PlayMode 桥接，供插件调用） */
    override fun setPlayMode(mode: String) {
        val playMode = try {
            com.winter.muplayer.model.PlayMode.valueOf(mode)
        } catch (_: IllegalArgumentException) {
            com.winter.muplayer.model.PlayMode.SEQUENTIAL
        }
        setPlayMode(playMode)
    }

    /** 获取当前队列（供插件查询） */
    override fun getQueue(): List<Track> {
        return queueManager.queue.value.map { it.track }
    }

    /** 当前曲目（IPlayerHost 快速访问） */
    override val currentTrack: Track?
        get() = _playerState.value.currentTrack

    /** 总时长毫秒 */
    override val duration: Long
        get() = _playerState.value.duration

    /** 当前进度毫秒 */
    override val progress: Long
        get() = _playerState.value.progress

    /** 是否正在播放 */
    override val isPlaying: Boolean
        get() = _playerState.value.state == com.winter.muplayer.model.PlayerState.PLAYING

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
