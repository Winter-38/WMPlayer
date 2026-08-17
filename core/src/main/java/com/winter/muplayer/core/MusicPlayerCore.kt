package com.winter.muplayer.core

import android.content.Context
import android.content.SharedPreferences
import com.winter.muplayer.core.engine.ExoPlayerEngine
import com.winter.muplayer.core.engine.PlayerEngine
import com.winter.muplayer.core.SettingsManager
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
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
class MusicPlayerCore private constructor(context: Context) {

    // ==================== 播放状态持久化常量 ====================

    companion object {
        private const val PLAYBACK_PREFS_NAME = "playback_state"
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        private const val KEY_QUEUE_IDS = "queue_ids"
        private const val KEY_QUEUE_INDEX = "queue_index"
        private const val KEY_LAST_POSITION = "last_position"

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

    /** 播放状态持久化 */
    private val playbackPrefs: SharedPreferences =
        context.getSharedPreferences(PLAYBACK_PREFS_NAME, Context.MODE_PRIVATE)

    /** 播放列表管理器～管着用户创建的那些歌单 */
    val playlistManager = PlaylistManager(context)

    /** 这是播放引擎，干活的是它 */
    val engine: PlayerEngine = ExoPlayerEngine(context)

    /** 进度追踪器，每隔一会儿就问引擎「播到哪了」 */
    private val progressTracker = ProgressTracker(scope, engine)

    /** 应用设置管理器 */
    val settings = SettingsManager(context)

    private val _playerState = MutableStateFlow(PlayerStateData())
    val playerState: StateFlow<PlayerStateData> = _playerState.asStateFlow()

    /** 独立进度流（播放时每 250ms 更新），只喂给进度条，避免高频进度污染 playerState 拖垮全 UI 重组 */
    val progressState: StateFlow<ProgressTracker.ProgressData> = progressTracker.progressState

    init {
        // 监听歌曲播完事件，自动切到下一首
        engine.setOnTrackEndListener {
            scope.launch {
                handleTrackCompletion()
            }
        }

        // 监听引擎状态变化，同步到自己的状态里并更新通知
        scope.launch {
            engine.playerState.collect { engineState ->
                val prevTrack = _playerState.value.currentTrack
                _playerState.update {
                    it.copy(
                        state = engineState.state,
                        error = engineState.error,
                        currentTrack = engineState.currentTrack
                    )
                }
                // 曲目切换时更新通知、保存播放状态
                if (engineState.currentTrack != null && engineState.currentTrack?.id != prevTrack?.id) {
                    MusicPlaybackService.currentService?.updateNotification()
                    savePlaybackState()
                }
            }
        }

        // 应用设置：默认播放模式、跨fade、音频焦点
        val savedMode = settings.defaultPlayMode
        if (savedMode != PlayMode.SEQUENTIAL) {
            setPlayMode(savedMode)
        }
        if (settings.crossfadeDurationMs > 0) {
            (engine as? ExoPlayerEngine)?.setCrossfadeDuration(settings.crossfadeDurationMs)
        }
        (engine as? ExoPlayerEngine)?.setAudioFocusDuck(settings.audioFocusDuck)
    }

    /**
     * 内部准备曲目——加载歌曲数据、重置进度、开始追踪播放进度
     */
    private suspend fun prepareTrackInternal(track: Track) {
        _playerState.update {
            it.copy(currentTrack = track)
        }
        engine.prepare(track)
        progressTracker.start()
    }

    /** 用于后台服务的 applicationContext */
    private val appContextForBg: android.content.Context = context

    /** 播放！如果还没准备好会自动先准备 */
    fun play() {
        AppLogger.i("Player", "play")
        if (isReleased) return
        // 启动前台服务并显示媒体通知
        MusicPlaybackService.start(appContextForBg)
        MusicPlaybackService.currentService?.startForegroundPlayback()
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
        AppLogger.i("Player", "pause")
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                engine.pause()
            }
            // 暂停后退前台，更新通知为暂停状态
            MusicPlaybackService.currentService?.pauseForegroundPlayback()
        }
    }

    /** 下一首～会根据当前的播放模式算出下一首是什么 */
    fun playNext() {
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
                        it.copy(state = PlayerState.IDLE)
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
        settings.defaultPlayMode = mode
        scope.launch {
            if (isReleased) return@launch
            queueManager.setPlayMode(mode)
            _playMode.value = mode
        }
    }

    /** 将曲目追加到播放队列末尾～ */
    fun addTrack(track: Track) {
        if (isReleased) return
        scope.launch {
            queueManager.enqueue(track)
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

    /**
     * 立即播放：将曲目插入播放队列顶部并立即播放。
     *
     * - 队列为空 → 直接作为单首歌曲播放
     * - 队列非空 → 插入到列表顶部（索引 0）并立即播放
     */
    fun playTrackTop(track: Track) {
        AppLogger.i("Player", "playTrackTop: ${track.title}")
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                progressTracker.stop()
                queueManager.enqueueTop(track)
                queueManager.setCurrentIndex(0)
                val currentTrack = queueManager.getCurrentTrack()
                if (currentTrack != null) {
                    prepareTrackInternal(currentTrack)
                    engine.play()
                }
            }
        }
    }

    /**
     * 智能播放：播放一首歌，并根据队列状态决定是否批量添加。
     *
     * - 队列为空 → 将 [batch] 中的所有歌曲加入队列末尾，播放 [track]
     * - 队列非空 → 将 [batch] 中的所有歌曲按顺序插入到当前播放之后，播放 [track]
     */
    fun playTrackSmart(track: Track, batch: List<Track>) {
        AppLogger.i("Player", "playTrackSmart: ${track.title} (batch=${batch.size})")
        if (isReleased) return
        scope.launch {
            engineMutex.withLock {
                if (queueManager.isEmpty()) {
                    // 队列为空：先添加所有 batch 歌曲，再定位到目标曲目
                    queueManager.enqueueAll(batch)
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
                    // 队列非空：将整个上下文列表按顺序插入到当前播放之后
                    val queueIds = queueManager.queue.value.map { it.track.id }
                    val batchIds = batch.map { it.id }
                    if (!queueIds.containsSlice(batchIds)) {
                        queueManager.enqueueNextAll(batch)
                    }
                    // 在队列中找到目标曲目的实际位置（不依赖 insertBase 假设）
                    val targetIndex = queueManager.queue.value.indexOfFirst { it.track.id == track.id }
                    if (targetIndex >= 0) {
                        queueManager.setCurrentIndex(targetIndex)
                    }
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

    /** 一首歌唱完了——根据播放模式决定下一步干啥 */
    private suspend fun handleTrackCompletion() {
        val lastTrack = _playerState.value.currentTrack
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
            PlayMode.REPEAT_ALL -> {
                val nextTrack = queueManager.getNextTrack()
                if (nextTrack != null) {
                    prepareTrackInternal(nextTrack)
                    engine.play()
                } else {
                    // 列表循环：播完最后一首回到队列开头
                    queueManager.setCurrentIndex(0)
                    val firstTrack = queueManager.getCurrentTrack()
                    if (firstTrack != null) {
                        prepareTrackInternal(firstTrack)
                        engine.play()
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
                        it.copy(state = PlayerState.IDLE)
                    }
                }
            }
        }
    }

    // ==================== 播放状态保存 / 恢复 ====================

    /**
     * 保存当前播放状态到 SharedPreferences。
     * 包括当前曲目 ID、队列中所有曲目 ID、当前索引和播放进度。
     */
    fun savePlaybackState() {
        val currentQueue = queueManager.queue.value
        val ids = currentQueue.map { it.track.id }
        val index = queueManager.currentIndex.value
        val currentTrackId = _playerState.value.currentTrack?.id ?: -1L
        val progress = progressTracker.progressState.value.progress

        playbackPrefs.edit()
            .putLong(KEY_LAST_TRACK_ID, currentTrackId)
            .putString(KEY_QUEUE_IDS, ids.joinToString(","))
            .putInt(KEY_QUEUE_INDEX, index)
            .putLong(KEY_LAST_POSITION, progress)
            .apply()
    }

    /**
     * 根据扫描到的完整曲目列表恢复上次的播放状态和队列。
     * 在 [com.winter.muplayer.core.scanner.LocalMusicScanner.scan] 完成后调用。
     *
     * @param allTracks 扫描到的全部本地曲目（用于将 ID 匹配为完整 Track 对象）
     */
    fun restorePlaybackState(allTracks: List<Track>) {
        val trackId = playbackPrefs.getLong(KEY_LAST_TRACK_ID, -1L)
        val idsStr = playbackPrefs.getString(KEY_QUEUE_IDS, "") ?: ""
        val savedIndex = playbackPrefs.getInt(KEY_QUEUE_INDEX, -1)
        val savedPosition = playbackPrefs.getLong(KEY_LAST_POSITION, 0L)

        if (idsStr.isEmpty() || trackId == -1L || savedIndex < 0) return

        val ids = idsStr.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return

        val trackMap = allTracks.associateBy { it.id }
        val restoredTracks = ids.mapNotNull { trackMap[it] }
        if (restoredTracks.isEmpty()) return

        scope.launch {
            engineMutex.withLock {
                queueManager.clear()
                queueManager.enqueueAll(restoredTracks)

                val targetIndex = ids.indexOf(trackId).coerceIn(0, restoredTracks.size - 1)
                queueManager.setCurrentIndex(targetIndex)

                val restoreTrack = queueManager.getCurrentTrack()
                if (restoreTrack != null) {
                    prepareTrackInternal(restoreTrack)
                }
            }
            // 恢复播放进度（seek 需要在引擎准备完成后执行）
            if (savedPosition > 0L) {
                engine.seekTo(savedPosition)
            }
        }
    }
}

/**
 * 检查列表中是否包含指定的连续子序列。
 */
private fun List<Long>.containsSlice(slice: List<Long>): Boolean {
    if (slice.isEmpty()) return true
    if (size < slice.size) return false
    for (i in 0..size - slice.size) {
        var match = true
        for (j in slice.indices) {
            if (this[i + j] != slice[j]) {
                match = false
                break
            }
        }
        if (match) return true
    }
    return false
}