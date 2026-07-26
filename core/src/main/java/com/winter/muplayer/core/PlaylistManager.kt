package com.winter.muplayer.core

import android.content.Context
import android.util.Log
import com.winter.muplayer.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "WMPlayer-Playlist"
private const val PLAYLIST_FILE = "playlists.json"

/**
 * 播放列表管理器 — 创建、删除、重命名播放列表，管理曲目成员。
 *
 * 数据持久化存储在 app 内部存储的 JSON 文件中。
 * 所有写操作通过 [Mutex] 保证线程安全并自动持久化。
 */
class PlaylistManager(context: Context) {

    private val file = File(context.filesDir, PLAYLIST_FILE)
    private val mutex = Mutex()

    private var nextId: Long = 1L

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        load()
    }

    // ==================== 读取 ====================

    /**
     * 根据 ID 查找播放列表。
     */
    fun findById(id: Long): Playlist? = _playlists.value.find { it.id == id }

    // ==================== 写入（自动持久化） ====================

    /**
     * 创建一个新的空播放列表。
     *
     * @param name 播放列表名称
     * @return 新创建的 [Playlist]
     */
    suspend fun create(name: String): Playlist = mutex.withLock {
        val playlist = Playlist(
            id = nextId++,
            name = name.trim().ifBlank { "未命名播放列表" },
            trackIds = emptyList(),
            createTime = System.currentTimeMillis()
        )
        _playlists.update { it + playlist }
        save()
        playlist
    }

    /**
     * 删除指定播放列表。
     */
    suspend fun delete(id: Long) = mutex.withLock {
        val removed = _playlists.value.find { it.id == id }
        _playlists.update { list -> list.filter { it.id != id } }
        save()
    }

    /**
     * 重命名播放列表。
     */
    suspend fun rename(id: Long, newName: String) = mutex.withLock {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id == id) playlist.copy(
                    name = newName.trim().ifBlank { "未命名播放列表" }
                ) else playlist
            }
        }
        save()
    }

    /**
     * 向播放列表添加一首曲目。
     * 如果曲目已存在则忽略。
     */
    suspend fun addTrack(playlistId: Long, trackId: Long) = mutex.withLock {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id == playlistId && trackId !in playlist.trackIds) {
                    playlist.copy(trackIds = playlist.trackIds + trackId)
                } else playlist
            }
        }
        save()
    }

    /**
     * 向播放列表批量添加曲目。
     */
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = mutex.withLock {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id == playlistId) {
                    val merged = (playlist.trackIds.toSet() + trackIds.toSet()).toList()
                    playlist.copy(trackIds = merged)
                } else playlist
            }
        }
        save()
    }

    /**
     * 从播放列表移除一首曲目。
     */
    suspend fun removeTrack(playlistId: Long, trackId: Long) = mutex.withLock {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(trackIds = playlist.trackIds - trackId)
                } else playlist
            }
        }
        save()
    }

    /**
     * 清空播放列表中的所有曲目。
     */
    suspend fun clearTracks(playlistId: Long) = mutex.withLock {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id == playlistId) playlist.copy(trackIds = emptyList()) else playlist
            }
        }
        save()
    }

    // ==================== 持久化 ====================

    /**
     * 从 JSON 文件加载播放列表。
     */
    private fun load() {
        if (!file.exists()) {
            _playlists.value = emptyList()
            nextId = 1L
            return
        }
        try {
            val json = JSONObject(file.readText())
            nextId = json.optLong("nextId", 1L)
            val arr = json.optJSONArray("playlists") ?: JSONArray()
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val trackIdsArr = obj.optJSONArray("trackIds") ?: JSONArray()
                val trackIds = mutableListOf<Long>()
                for (j in 0 until trackIdsArr.length()) {
                    trackIds.add(trackIdsArr.getLong(j))
                }
                list.add(
                    Playlist(
                        id = obj.getLong("id"),
                        name = obj.getString("name"),
                        trackIds = trackIds,
                        createTime = obj.getLong("createTime")
                    )
                )
            }
            _playlists.value = list
        } catch (e: Exception) {
            Log.w(TAG, "load: failed to parse playlists.json, starting fresh", e)
            _playlists.value = emptyList()
            nextId = 1L
        }
    }

    /**
     * 将播放列表保存到 JSON 文件。
     * 仅在 mutex 内部调用。
     */
    private fun save() {
        try {
            val arr = JSONArray()
            for (playlist in _playlists.value) {
                val trackIdsArr = JSONArray()
                for (tid in playlist.trackIds) {
                    trackIdsArr.put(tid)
                }
                arr.put(
                    JSONObject().apply {
                        put("id", playlist.id)
                        put("name", playlist.name)
                        put("trackIds", trackIdsArr)
                        put("createTime", playlist.createTime)
                    }
                )
            }
            val json = JSONObject().apply {
                put("nextId", nextId)
                put("playlists", arr)
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "save: failed to write playlists.json", e)
        }
    }
}
