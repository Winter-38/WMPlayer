package com.winter.muplayer.core

import com.winter.muplayer.engine.PlayerEngine
import com.winter.muplayer.model.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 播放进度的「小闹钟」～
 * 它会不停地问播放器「现在播到哪了？」，然后把进度信息告诉 UI。
 * 播放的时候就每秒问好几次，暂停了就停下来歇口气。
 */
class ProgressTracker(
    private val scope: CoroutineScope,
    private val engine: PlayerEngine
) {
    private val _progressState = MutableStateFlow(ProgressData())
    val progressState: StateFlow<ProgressData> = _progressState.asStateFlow()

    private var progressUpdateJob: Job? = null
    private var stateCollectionJob: Job? = null

    data class ProgressData(
        val progress: Long = 0L,
        val duration: Long = 0L
    )

    fun start() {
        stop()
        stateCollectionJob = scope.launch {
            engine.playerState.collect { state ->
                when (state.state) {
                    PlayerState.PLAYING -> {
                        startProgressUpdates()
                    }
                    PlayerState.PAUSED -> {
                        stopProgressUpdates()
                        // 暂停的时候更新一次当前的进度，让 UI 显示正确的数值
                        _progressState.update {
                            ProgressData(
                                progress = engine.getCurrentPosition(),
                                duration = engine.getDuration()
                            )
                        }
                    }
                    PlayerState.ERROR, PlayerState.IDLE -> {
                        stopProgressUpdates()
                        if (state.state == PlayerState.IDLE) {
                            _progressState.update { ProgressData() }
                        }
                    }
                    else -> {
                        // 加载中……先不更新进度
                    }
                }
            }
        }
    }

    fun stop() {
        stopProgressUpdates()
        stateCollectionJob?.cancel()
        stateCollectionJob = null
    }

    private fun startProgressUpdates() {
        if (progressUpdateJob?.isActive == true) return
        progressUpdateJob = scope.launch {
            while (isActive && engine.isReady()) {
                _progressState.update {
                    ProgressData(
                        progress = engine.getCurrentPosition(),
                        duration = engine.getDuration()
                    )
                }
                // 每 250 毫秒问一次，不会太频繁也不会漏掉～
                delay(250L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
}
