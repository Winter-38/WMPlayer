package com.winter.muplayer.core

import com.winter.muplayer.core.engine.PlayerEngine
import com.winter.muplayer.model.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
            // 注意：不能用 engine.isReady() 作为 while 条件 —— 播放过程中
            // 短暂离开 READY（seek 缓冲、网络/IO 卡顿等）会导致循环退出且
            // 不会重启，进度条从此冻结。改为循环常驻，仅 READY 时更新。
            while (isActive) {
                if (engine.isReady()) {
                    _progressState.update {
                        ProgressData(
                            progress = engine.getCurrentPosition(),
                            duration = engine.getDuration()
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
}
