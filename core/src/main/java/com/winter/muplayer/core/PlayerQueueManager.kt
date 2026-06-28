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
 *
 * 队列操作支持两种语义：
 * - [enqueue] / [enqueueAll]：追加到队列末尾（FIFO）
 * - [enqueueNext] / [enqueueNextAll]：插入到当前播放位置之后（"下一首播放"）
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

    // ==================== 队列操作 ====================

    /**
     * 将一首歌追加到队列末尾（FIFO 行为）。
     * 如果队列为空，自动成为当前曲目。
     */
    suspend fun enqueue(track: Track) = mutex.withLock {
        val entry = QueueEntry(uid = uidCounter++, track = track)
        val wasEmpty = _queue.value.isEmpty()
        _queue.update { it + entry }
        if (wasEmpty) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            val newIndex = _queue.value.size - 1
            shuffleIndices = shuffleIndices + newIndex
        }
    }

    /**
     * 批量追加到队列末尾。
     * 保持 tracks 的原始顺序。
     */
    suspend fun enqueueAll(tracks: List<Track>) = mutex.withLock {
        val entries = tracks.map { QueueEntry(uid = uidCounter++, track = it) }
        val wasEmpty = _queue.value.isEmpty()
        val oldSize = _queue.value.size
        _queue.update { it + entries }
        if (wasEmpty) {
            _currentIndex.value = 0
        }
        if (playMode == PlayMode.SHUFFLE) {
            for (i in oldSize until _queue.value.size) {
                shuffleIndices = shuffleIndices + i
            }
        }
    }

    /**
     * 将一首歌插入到当前播放位置之后（"下一首播放"）。
     * 当前正在播放的歌曲不受影响，播完它后自动切到新歌。
     *
     * @return 新歌在队列中的索引位置
     */
    suspend fun enqueueNext(track: Track): Int = mutex.withLock {
        val entry = QueueEntry(uid = uidCounter++, track = track)
        val list = _queue.value
        if (list.isEmpty()) {
            _queue.update { listOf(entry) }
            _currentIndex.value = 0
            return@withLock 0
        }
        val insertPos = _currentIndex.value + 1
        _queue.update { list ->
            list.subList(0, insertPos) + entry + list.subList(insertPos, list.size)
        }
        if (playMode == PlayMode.SHUFFLE) {
            insertIntoShuffleIndices(insertPos)
        }
        return@withLock insertPos
    }

    /**
     * 批量插入到当前播放位置之后（"下一首播放"）。
     * tracks 的顺序保持不变：第一首紧跟在当前歌曲之后。
     */
    suspend fun enqueueNextAll(tracks: List<Track>) = mutex.withLock {
        val entries = tracks.map { QueueEntry(uid = uidCounter++, track = it) }
        val list = _queue.value
        if (list.isEmpty()) {
            _queue.update { entries }
            _currentIndex.value = 0
            return@withLock
        }
        val insertPos = _currentIndex.value + 1
        _queue.update { list ->
            list.subList(0, insertPos) + entries + list.subList(insertPos, list.size)
        }
        if (playMode == PlayMode.SHUFFLE) {
            for (i in entries.indices) {
                insertIntoShuffleIndices(insertPos + i)
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
            PlayMode.REPEAT_ALL -> {
                val next = _currentIndex.value + 1
                if (next < list.size) {
                    _currentIndex.value = next
                    list[next].track
                } else {
                    _currentIndex.value = 0
                    list[0].track
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
            PlayMode.REPEAT_ALL -> {
                val prev = _currentIndex.value - 1
                if (prev >= 0) {
                    _currentIndex.value = prev
                    list[prev].track
                } else {
                    _currentIndex.value = list.size - 1
                    list.last().track
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
     *
     * 注意：在插入新索引之前，会先把 shuffleIndices 中所有 >= newIndex 的旧索引 +1，
     * 因为队列中这些位置的元素已经往后移了一位。
     */
    private fun insertIntoShuffleIndices(newIndex: Int) {
        if (shuffleIndices.isEmpty()) {
            shuffleIndices = listOf(newIndex)
            shufflePosition = 0
            return
        }
        // 队列中在 newIndex 及之后的元素都往后移了一位，对应的索引也要 +1
        val adjusted = shuffleIndices.map { if (it >= newIndex) it + 1 else it }
        val insertPos = (0..adjusted.size).random()
        val mutableList = adjusted.toMutableList()
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
