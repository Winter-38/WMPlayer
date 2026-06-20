package com.winter.muplayer.core

import android.util.Log
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "WMPlayer-Queue"

/**
 * 队列中的唯一条目，每个条目拥有唯一的 [uid]。
 * 即使同一首歌被多次添加，每条的 uid 也不同。
 */
data class QueueEntry(
    val uid: Long,
    val track: Track
)

class PlayQueueManager {

    private val mutex = Mutex()
    private var uidCounter = 0L

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var shuffleIndices: List<Int> = emptyList()
    private var shufflePosition: Int = -1

    private var playMode: PlayMode = PlayMode.SEQUENTIAL

    suspend fun setPlayMode(mode: PlayMode) = mutex.withLock {
        val old = playMode
        playMode = mode
        if (mode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
        Log.d(TAG, "setPlayMode: $old → $mode, size=${_queue.value.size}, currentIndex=${_currentIndex.value}")
    }

    suspend fun getPlayMode(): PlayMode = mutex.withLock {
        playMode
    }

    suspend fun addTrack(track: Track) = mutex.withLock {
        val entry = QueueEntry(uid = uidCounter++, track = track)
        _queue.update { currentList -> currentList + entry }
        val prevIdx = _currentIndex.value
        if (_currentIndex.value == -1 && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
        Log.d(TAG, "addTrack: uid=${entry.uid}, track='${track.title}' (id=${track.id}), " +
                "size=${_queue.value.size}, currentIndex=${_currentIndex.value} (was $prevIdx)")
    }

    suspend fun addTracks(tracks: List<Track>) = mutex.withLock {
        val entries = tracks.map { track ->
            QueueEntry(uid = uidCounter++, track = track)
        }
        _queue.update { currentList -> currentList + entries }
        val prevIdx = _currentIndex.value
        if (_currentIndex.value == -1 && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
        Log.d(TAG, "addTracks: count=${tracks.size}, uids=[${entries.joinToString { it.uid.toString() }}], " +
                "size=${_queue.value.size}, currentIndex=${_currentIndex.value} (was $prevIdx)")
    }

    suspend fun removeTrack(index: Int) = mutex.withLock {
        val currentList = _queue.value
        if (index !in currentList.indices) {
            Log.w(TAG, "removeTrack: index=$index out of bounds (size=${currentList.size}), ignored")
            return@withLock
        }

        val removed = currentList[index]
        _queue.update { list ->
            list.toMutableList().apply { removeAt(index) }
        }

        val newList = _queue.value
        val currentIdx = _currentIndex.value
        Log.d(TAG, "removeTrack: index=$index, removed uid=${removed.uid} track='${removed.track.title}' (id=${removed.track.id}), " +
                "newSize=${newList.size}, oldCurrentIndex=$currentIdx")

        when {
            currentIdx > index -> {
                _currentIndex.value = currentIdx - 1
                Log.d(TAG, "removeTrack: currentIndex adjusted down: $currentIdx → ${_currentIndex.value}")
            }
            currentIdx == index -> {
                if (newList.isEmpty()) {
                    _currentIndex.value = -1
                    Log.d(TAG, "removeTrack: queue now empty, currentIndex → -1")
                } else if (index < newList.size) {
                    _currentIndex.value = index
                    Log.d(TAG, "removeTrack: currentIndex stayed at $index, now points to next item")
                } else {
                    _currentIndex.value = newList.size - 1
                    Log.d(TAG, "removeTrack: currentIndex adjusted to last: ${_currentIndex.value}")
                }
            }
        }
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
    }

    suspend fun clear() = mutex.withLock {
        val oldSize = _queue.value.size
        _queue.value = emptyList()
        _currentIndex.value = -1
        shuffleIndices = emptyList()
        shufflePosition = -1
        Log.d(TAG, "clear: removed $oldSize items, currentIndex → -1")
    }

    suspend fun getCurrentTrack(): Track? = mutex.withLock {
        val idx = _currentIndex.value
        val list = _queue.value
        val track = if (idx in list.indices) list[idx].track else null
        Log.d(TAG, "getCurrentTrack: index=$idx, track='${track?.title ?: "null"}'")
        track
    }

    suspend fun getNextTrack(): Track? = mutex.withLock {
        val list = _queue.value
        if (list.isEmpty()) {
            Log.d(TAG, "getNextTrack: queue empty, return null")
            return@withLock null
        }

        val result = when (playMode) {
            PlayMode.SEQUENTIAL -> {
                val next = _currentIndex.value + 1
                if (next < list.size) {
                    _currentIndex.value = next
                    list[next].track
                } else {
                    null
                }
            }
            PlayMode.SINGLE_LOOP -> {
                val idx = _currentIndex.value
                if (idx in list.indices) list[idx].track else null
            }
            PlayMode.SHUFFLE -> {
                if (shuffleIndices.isEmpty()) {
                    null
                } else {
                    shufflePosition = (shufflePosition + 1) % shuffleIndices.size
                    val realIndex = shuffleIndices[shufflePosition]
                    _currentIndex.value = realIndex
                    list[realIndex].track
                }
            }
        }
        Log.d(TAG, "getNextTrack: mode=$playMode, currentIndex → ${_currentIndex.value}, " +
                "track='${result?.title ?: "null"}', shufflePos=$shufflePosition")
        result
    }

    suspend fun getPreviousTrack(): Track? = mutex.withLock {
        val list = _queue.value
        if (list.isEmpty()) {
            Log.d(TAG, "getPreviousTrack: queue empty, return null")
            return@withLock null
        }

        val result = when (playMode) {
            PlayMode.SEQUENTIAL -> {
                val prev = _currentIndex.value - 1
                if (prev >= 0) {
                    _currentIndex.value = prev
                    list[prev].track
                } else {
                    null
                }
            }
            PlayMode.SINGLE_LOOP -> {
                val idx = _currentIndex.value
                if (idx in list.indices) list[idx].track else null
            }
            PlayMode.SHUFFLE -> {
                if (shuffleIndices.isEmpty()) {
                    null
                } else {
                    shufflePosition = if (shufflePosition - 1 < 0) {
                        shuffleIndices.size - 1
                    } else {
                        shufflePosition - 1
                    }
                    val realIndex = shuffleIndices[shufflePosition]
                    _currentIndex.value = realIndex
                    list[realIndex].track
                }
            }
        }
        Log.d(TAG, "getPreviousTrack: mode=$playMode, currentIndex → ${_currentIndex.value}, " +
                "track='${result?.title ?: "null"}', shufflePos=$shufflePosition")
        result
    }

    suspend fun setCurrentIndex(index: Int) = mutex.withLock {
        val list = _queue.value
        val oldIdx = _currentIndex.value
        if (index in list.indices) {
            _currentIndex.value = index
            if (playMode == PlayMode.SHUFFLE && shuffleIndices.isNotEmpty()) {
                shufflePosition = shuffleIndices.indexOf(index).coerceAtLeast(0)
            }
            Log.d(TAG, "setCurrentIndex: $oldIdx → $index, " +
                    "track='${list[index].track.title}', shufflePos=$shufflePosition")
        } else {
            Log.w(TAG, "setCurrentIndex: index=$index out of bounds (size=${list.size}), ignored")
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
        Log.d(TAG, "rebuildShuffleIndices: indices=$shuffleIndices, position=$shufflePosition")
    }
}
