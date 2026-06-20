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
 * 队列里的每一「条」～
 * 每首歌被加进队列时都会包上这个外壳，获得一个唯一的 [uid]。
 * 就算同一首歌加了两遍，这两条的 uid 也不一样，这样删其中一条就不会影响到另一条啦！
 */
data class QueueEntry(
    val uid: Long,
    val track: Track
)

/**
 * 播放队列管理器～
 * 管着当前要播什么歌、播到哪一首了、按什么顺序播。
 * 支持顺序播放、随机打乱、单曲循环三种模式，
 * 所有操作都是线程安全的，放心用！
 */
class PlayQueueManager {

    private val mutex = Mutex()
    private var uidCounter = 0L

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 随机播放的索引序列，里面存的都是 _queue 的真实下标 */
    private var shuffleIndices: List<Int> = emptyList()
    /** 当前在 shuffle 序列中的位置 */
    private var shufflePosition: Int = -1

    private var playMode: PlayMode = PlayMode.SEQUENTIAL

    /** 切换播放模式～切到随机模式时会自动重建随机序列 */
    suspend fun setPlayMode(mode: PlayMode) = mutex.withLock {
        val old = playMode
        playMode = mode
        if (mode == PlayMode.SHUFFLE) {
            rebuildShuffleIndices()
        }
    }

    suspend fun getPlayMode(): PlayMode = mutex.withLock {
        playMode
    }

    /** 把一首歌加到队列末尾～如果队列本来是空的，它会自动变成当前曲目 */
    suspend fun addTrack(track: Track) = mutex.withLock {
        val entry = QueueEntry(uid = uidCounter++, track = track)
        val newIndex = _queue.value.size
        _queue.update { currentList -> currentList + entry }
        val prevIdx = _currentIndex.value
        if (_currentIndex.value == -1 && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            insertIntoShuffleIndices(newIndex)
        }
    }

    /** 批量加歌！一次丢一堆进去～ */
    suspend fun addTracks(tracks: List<Track>) = mutex.withLock {
        val entries = tracks.map { track ->
            QueueEntry(uid = uidCounter++, track = track)
        }
        val startIndex = _queue.value.size
        _queue.update { currentList -> currentList + entries }
        val prevIdx = _currentIndex.value
        if (_currentIndex.value == -1 && _queue.value.isNotEmpty()) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            for (i in startIndex until _queue.value.size) {
                insertIntoShuffleIndices(i)
            }
        }
    }

    /** 从队列中移除指定位置的歌～会自动调整当前索引以免乱掉 */
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

        // 删除后当前索引可能需要调整
        when {
            currentIdx > index -> _currentIndex.value = currentIdx - 1
            currentIdx == index -> {
                if (newList.isEmpty()) {
                    _currentIndex.value = -1
                } else if (index < newList.size) {
                    _currentIndex.value = index
                } else {
                    _currentIndex.value = newList.size - 1
                }
            }
        }
        if (playMode == PlayMode.SHUFFLE) {
            removeFromShuffleIndices(index)
        }
    }

    /** 清空整个队列，所有状态归零～ */
    suspend fun clear() = mutex.withLock {
        val oldSize = _queue.value.size
        _queue.value = emptyList()
        _currentIndex.value = -1
        shuffleIndices = emptyList()
        shufflePosition = -1
    }

    /** 当前在播哪首歌？没有在播就返回 null */
    suspend fun getCurrentTrack(): Track? = mutex.withLock {
        val idx = _currentIndex.value
        val list = _queue.value
        return if (idx in list.indices) list[idx].track else null
    }

    /** 下一首是什么？会根据当前播放模式算出下一首的索引 */
    suspend fun getNextTrack(): Track? = mutex.withLock {
        val list = _queue.value
        if (list.isEmpty()) return@withLock null

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
        result
    }

    /** 上一首是什么？逻辑和下一首差不多，就是方向反过来了～ */
    suspend fun getPreviousTrack(): Track? = mutex.withLock {
        val list = _queue.value
        if (list.isEmpty()) return@withLock null

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
        result
    }

    /** 直接跳到队列中的指定位置～ */
    suspend fun setCurrentIndex(index: Int) = mutex.withLock {
        val list = _queue.value
        if (index !in list.indices) {
            Log.w(TAG, "setCurrentIndex: index=$index out of bounds (size=${list.size}), ignored")
            return@withLock
        }
        _currentIndex.value = index
        if (playMode == PlayMode.SHUFFLE && shuffleIndices.isNotEmpty()) {
            shufflePosition = shuffleIndices.indexOf(index).coerceAtLeast(0)
        }
    }

    suspend fun getQueueSize(): Int = mutex.withLock {
        _queue.value.size
    }

    suspend fun isEmpty(): Boolean = mutex.withLock {
        _queue.value.isEmpty()
    }

    /**
     * 全量重建随机播放序列～
     * 只有第一次切到随机模式的时候才调这个，
     * 后面加歌删歌都是用增量更新，不会全部打乱重新来。
     */
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

    /**
     * 增量插入新歌到随机序列里～
     * 比全量重建温柔多了，不会把已经播过的顺序打乱。
     * 新歌会被随机插到序列的某个位置。
     */
    private fun insertIntoShuffleIndices(newIndex: Int) {
        if (shuffleIndices.isEmpty()) {
            shuffleIndices = listOf(newIndex)
            shufflePosition = 0
            return
        }
        val insertPos = (0..shuffleIndices.size).random()
        val mutableList = shuffleIndices.toMutableList()
        mutableList.add(insertPos, newIndex)
        shuffleIndices = mutableList
        if (insertPos <= shufflePosition) {
            shufflePosition++
        }
    }

    /**
     * 从随机序列中移除一首歌～
     * 删掉之后还会把后面所有索引往前挪一位，保持序列不乱。
     */
    private fun removeFromShuffleIndices(removedIndex: Int) {
        if (shuffleIndices.isEmpty()) return

        val posInShuffle = shuffleIndices.indexOf(removedIndex)
        val mutableList = shuffleIndices.toMutableList()

        if (posInShuffle >= 0) {
            mutableList.removeAt(posInShuffle)
            shuffleIndices = mutableList.map { if (it > removedIndex) it - 1 else it }
            when {
                shuffleIndices.isEmpty() -> shufflePosition = -1
                posInShuffle < shufflePosition -> shufflePosition--
                else -> shufflePosition = shufflePosition.coerceAtMost(shuffleIndices.size - 1)
            }
        } else {
            shuffleIndices = mutableList.map { if (it > removedIndex) it - 1 else it }
        }
    }
}
