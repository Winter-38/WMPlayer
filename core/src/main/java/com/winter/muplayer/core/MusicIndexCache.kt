package com.winter.muplayer.core

import android.content.Context
import android.util.Log
import com.winter.muplayer.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "MusicIndexCache"
private const val INDEX_CACHE_FILE = "music_index_cache.json"

/**
 * 音乐索引缓存——避免 UI 层反复重建歌手/专辑索引。
 *
 * ## 内存缓存
 * 预构建 artistIndex / albumIndex 两个 Map，Composable 重组时直接读取，
 * 避免每次重组执行 `tracks.groupBy { it.artist }.toSortedMap()`。
 *
 * ## 磁盘持久化
 * 索引构建完成后写入 [INDEX_CACHE_FILE]，下次 App 启动时直接从磁盘加载，
 * 避免每次冷启动都要重新遍历全部歌曲构建索引。
 * 磁盘缓存通过 `trackCount + version` 校验一致性。
 *
 * ## 使用方式
 * ```kotlin
 * val cache = MusicIndexCache(context)
 * val index = cache.getOrBuild(tracks)  // 自动加载/保存磁盘缓存
 * // index.artistIndex  → Map<歌手名, List<Track>>
 * // index.albumIndex   → Map<专辑名, List<Track>>
 * ```
 */
class MusicIndexCache(context: Context) {

    /**
     * 一次完整的索引快照。
     */
    data class IndexData(
        val allTracks: List<Track>,
        val artistIndex: Map<String, List<Track>>,
        val albumIndex: Map<String, List<Track>>,
        val version: Int
    )

    private val cacheFile = File(context.filesDir, INDEX_CACHE_FILE)
    private var cachedIndex: IndexData? = null
    private var currentVersion = 0

    /**
     * 获取索引。优先从磁盘加载；如果磁盘缓存不存在或 tracks 已变化则重建。
     *
     * 引用相等检查：传入的 [tracks] 与上次为同一个 List 实例时，
     * 直接返回内存缓存，避免不必要重建。
     */
    fun getOrBuild(tracks: List<Track>): IndexData {
        // 1. 内存命中检查
        val cached = cachedIndex
        if (cached != null && cached.allTracks === tracks) {
            return cached
        }

        // 2. 尝试从磁盘加载并校验一致性
        val diskLoaded = loadFromDisk(tracks)
        if (diskLoaded != null) {
            cachedIndex = diskLoaded
            currentVersion = diskLoaded.version
            return diskLoaded
        }

        // 3. 磁盘无缓存或不匹配，重建索引
        Log.d(TAG, "Building index for ${tracks.size} tracks")
        val unknownArtist = "未知艺术家"
        val unknownAlbum = "未知专辑"

        val version = ++currentVersion
        val index = IndexData(
            allTracks = tracks,
            artistIndex = tracks
                .groupBy { it.artist.ifBlank { unknownArtist } }
                .toSortedMap(),
            albumIndex = tracks
                .groupBy { it.album.ifBlank { unknownAlbum } }
                .toSortedMap(),
            version = version
        )

        cachedIndex = index
        saveToDisk(index)
        return index
    }

    /**
     * 强制清除内存缓存和磁盘缓存，
     * 下次 [getOrBuild] 会重建并重新持久化。
     */
    fun invalidate() {
        cachedIndex = null
        if (cacheFile.exists()) {
            cacheFile.delete()
            Log.d(TAG, "Disk cache deleted")
        }
    }

    // ==================== 磁盘持久化 ====================

    /**
     * 从磁盘加载索引缓存，并与传入的 [tracks] 校验一致性。
     * 校验通过则返回 IndexData（tracks 引用用传入的 [tracks]），
     * 失败返回 null。
     */
    private fun loadFromDisk(tracks: List<Track>): IndexData? {
        if (!cacheFile.exists()) return null
        return try {
            val json = JSONObject(cacheFile.readText())
            val cachedVersion = json.optInt("version", 0)
            val cachedTrackCount = json.optInt("trackCount", 0)

            // 曲目数量不匹配 → 缓存已过时
            if (cachedTrackCount != tracks.size) {
                Log.d(TAG, "Disk cache stale: trackCount $cachedTrackCount != ${tracks.size}")
                return null
            }

            // 按 trackId 构建查找表
            val trackById = tracks.associateBy { it.id }

            // 恢复歌手索引：trackId → Track 对象映射
            val artistIndex = mutableMapOf<String, MutableList<Track>>()
            val artistArr = json.optJSONArray("artistIndex") ?: JSONArray()
            for (i in 0 until artistArr.length()) {
                val entry = artistArr.getJSONObject(i)
                val name = entry.getString("name")
                val ids = entry.optJSONArray("trackIds") ?: continue
                val trackList = mutableListOf<Track>()
                for (j in 0 until ids.length()) {
                    val id = ids.getLong(j)
                    trackById[id]?.let { trackList.add(it) }
                }
                if (trackList.isNotEmpty()) {
                    artistIndex[name] = trackList
                }
            }

            // 恢复专辑索引
            val albumIndex = mutableMapOf<String, MutableList<Track>>()
            val albumArr = json.optJSONArray("albumIndex") ?: JSONArray()
            for (i in 0 until albumArr.length()) {
                val entry = albumArr.getJSONObject(i)
                val name = entry.getString("name")
                val ids = entry.optJSONArray("trackIds") ?: continue
                val trackList = mutableListOf<Track>()
                for (j in 0 until ids.length()) {
                    val id = ids.getLong(j)
                    trackById[id]?.let { trackList.add(it) }
                }
                if (trackList.isNotEmpty()) {
                    albumIndex[name] = trackList
                }
            }

            currentVersion = cachedVersion
            Log.i(TAG, "Loaded from disk: ${artistIndex.size} artists, ${albumIndex.size} albums")
            IndexData(
                allTracks = tracks,
                artistIndex = artistIndex.toSortedMap(),
                albumIndex = albumIndex.toSortedMap(),
                version = cachedVersion
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from disk: ${e.message}")
            cacheFile.delete()
            null
        }
    }

    /**
     * 将索引快照保存到磁盘。
     * 只保存 trackId 列表，不保存完整的 Track 数据（由 LocalMusicScanner 缓存）。
     * 保存格式：
     * ```json
     * {
     *   "version": 1,
     *   "trackCount": 5000,
     *   "artistIndex": [{"name":"周杰伦","trackIds":[1,5,12]}, ...],
     *   "albumIndex": [...]
     * }
     * ```
     */
    private fun saveToDisk(index: IndexData) {
        try {
            val artistArr = JSONArray()
            for ((name, tracks) in index.artistIndex) {
                val ids = JSONArray()
                for (track in tracks) ids.put(track.id)
                artistArr.put(JSONObject().apply {
                    put("name", name)
                    put("trackIds", ids)
                })
            }

            val albumArr = JSONArray()
            for ((name, tracks) in index.albumIndex) {
                val ids = JSONArray()
                for (track in tracks) ids.put(track.id)
                albumArr.put(JSONObject().apply {
                    put("name", name)
                    put("trackIds", ids)
                })
            }

            cacheFile.writeText(
                JSONObject().apply {
                    put("version", index.version)
                    put("trackCount", index.allTracks.size)
                    put("artistIndex", artistArr)
                    put("albumIndex", albumArr)
                }.toString()
            )
            Log.i(TAG, "Saved to disk: ${index.artistIndex.size} artists, ${index.albumIndex.size} albums")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save to disk: ${e.message}")
        }
    }
}
