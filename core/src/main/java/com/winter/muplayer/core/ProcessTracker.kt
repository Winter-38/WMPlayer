package com.winter.muplayer.core

import com.winter.muplayer.engine.PlayerEngine
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
                        // Update once when paused
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
                        // LOADING, do nothing
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
                delay(250L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
}