package com.winter.muplayer.core

import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlayQueueManager {

    private val mutex = Mutex()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var shuffleIndices: List<Int> = emptyList()
    private var shufflePosition: Int = -1

    private var playMode: PlayMode = PlayMode.SEQUENTIAL

    suspend fun setPlayMode(mode: PlayMode) = mutex.withLock {
        playMode = mode
        if (mode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
    }

    suspend fun getPlayMode(): PlayMode = mutex.withLock {
        playMode
    }

    suspend fun addTrack(track: Track) = mutex.withLock {
        _queue.update { currentList -> currentList + track }
        if (_currentIndex.value == -1 && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
    }

    suspend fun addTracks(tracks: List<Track>) = mutex.withLock {
        _queue.update { currentList -> currentList + tracks }
        if (_currentIndex.value == -1 && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
    }

    suspend fun removeTrack(index: Int) = mutex.withLock {
        val currentList = _queue.value
        if (index !in currentList.indices) return@withLock

        _queue.update { list ->
            list.toMutableList().apply { removeAt(index) }
        }

        val newList = _queue.value
        val currentIdx = _currentIndex.value
        when {
            currentIdx > index -> {
                _currentIndex.value = currentIdx - 1
            }
            currentIdx == index -> {
                if (newList.isEmpty()) {
                    _currentIndex.value = -1
                } else if (index < newList.size) {
                    // Keep the same index, now pointing to the next track
                    _currentIndex.value = index
                } else {
                    // Removed the last element, index now out of bounds
                    _currentIndex.value = newList.size - 1
                }
            }
        }
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
    }

    suspend fun clear() = mutex.withLock {
        _queue.value = emptyList()
        _currentIndex.value = -1
        shuffleIndices = emptyList()
        shufflePosition = -1
    }

    suspend fun getCurrentTrack(): Track? = mutex.withLock {
        val idx = _currentIndex.value
        val list = _queue.value
        if (idx in list.indices) list[idx] else null
    }

    /**
     * Returns the next track based on the current play mode.
     * - [com.winter.muplayer.model.PlayMode.SEQUENTIAL]: Advances to the next index; returns `null` if at the end.
     * - [com.winter.muplayer.model.PlayMode.SHUFFLE]: Advances in the shuffled list; **loops infinitely**, never returns `null` as long as the queue is not empty.
     * - [com.winter.muplayer.model.PlayMode.SINGLE_LOOP]: Returns the current track without changing the index.
     */
    suspend fun getNextTrack(): Track? = mutex.withLock {
        val list = _queue.value
        if (list.isEmpty()) return@withLock null

        when (playMode) {
            PlayMode.SEQUENTIAL -> {
                val next = _currentIndex.value + 1
                if (next < list.size) {
                    _currentIndex.value = next
                    list[next]
                } else {
                    null
                }
            }
            PlayMode.SINGLE_LOOP -> {
                // Return current track without index change
                val idx = _currentIndex.value
                if (idx in list.indices) list[idx] else null
            }
            PlayMode.SHUFFLE -> {
                if (shuffleIndices.isEmpty()) return@withLock null
                shufflePosition = (shufflePosition + 1) % shuffleIndices.size
                val realIndex = shuffleIndices[shufflePosition]
                _currentIndex.value = realIndex
                list[realIndex]
            }
        }
    }

    /**
     * Returns the previous track based on the current play mode.
     * - [com.winter.muplayer.model.PlayMode.SEQUENTIAL]: Goes back to the previous index; returns `null` if at the beginning.
     * - [com.winter.muplayer.model.PlayMode.SHUFFLE]: Goes backwards in the shuffled list; **loops infinitely**, never returns `null` as long as the queue is not empty.
     * - [com.winter.muplayer.model.PlayMode.SINGLE_LOOP]: Returns the current track without changing the index.
     */
    suspend fun getPreviousTrack(): Track? = mutex.withLock {
        val list = _queue.value
        if (list.isEmpty()) return@withLock null

        when (playMode) {
            PlayMode.SEQUENTIAL -> {
                val prev = _currentIndex.value - 1
                if (prev >= 0) {
                    _currentIndex.value = prev
                    list[prev]
                } else {
                    null
                }
            }
            PlayMode.SINGLE_LOOP -> {
                val idx = _currentIndex.value
                if (idx in list.indices) list[idx] else null
            }
            PlayMode.SHUFFLE -> {
                if (shuffleIndices.isEmpty()) return@withLock null
                shufflePosition = if (shufflePosition - 1 < 0) {
                    shuffleIndices.size - 1
                } else {
                    shufflePosition - 1
                }
                val realIndex = shuffleIndices[shufflePosition]
                _currentIndex.value = realIndex
                list[realIndex]
            }
        }
    }

    suspend fun setCurrentIndex(index: Int) = mutex.withLock {
        val list = _queue.value
        if (index in list.indices) {
            _currentIndex.value = index
            if (playMode == PlayMode.SHUFFLE && shuffleIndices.isNotEmpty()) {
                shufflePosition = shuffleIndices.indexOf(index).coerceAtLeast(0)
            }
        }
    }

    suspend fun getQueueSize(): Int = mutex.withLock {
        _queue.value.size
    }

    suspend fun isEmpty(): Boolean = mutex.withLock {
        _queue.value.isEmpty()
    }

    private fun rebuildShuffleIndices() {
        val list = _queue.value
        if (list.isEmpty()) {
            shuffleIndices = emptyList()
            shufflePosition = -1
            return
        }
        shuffleIndices = list.indices.toList().shuffled()
        val currentIdx = _currentIndex.value
        shufflePosition = if (currentIdx in shuffleIndices.indices) {
            shuffleIndices.indexOf(currentIdx).coerceAtLeast(0)
        } else {
            0
        }
    }
}