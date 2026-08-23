package com.winter.muplayer.ui.browser

import com.winter.muplayer.core.SettingsManager
import com.winter.muplayer.ui.R

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.winter.muplayer.model.Track

/**
 * 音乐浏览器全局状态 —— 通过 CompositionLocal 注入，Tab / 排序 / 列表共享。
 * 任意组件修改此状态，所有读取该属性的组件自动重组。
 */
class MusicBrowserState(
    initialTracks: List<Track> = emptyList(),
    private val settings: SettingsManager? = null,
) {
    var tracks by mutableStateOf(initialTracks)
    var isLoading by mutableStateOf(true)
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf(MusicCategory.ALL)

    // 旧版本持久化的值可能为 4（“类型”排序项已移除），读取时收敛到有效范围
    private val _sortField = mutableIntStateOf((settings?.sortField ?: 0).coerceIn(0, 3))
    /** 排序字段：0=名称 1=时长 2=大小 3=日期（写入 SettingsManager 持久化） */
    var sortField: Int
        get() = _sortField.intValue
        set(value) {
            _sortField.intValue = value
            settings?.sortField = value
        }

    private val _sortAsc = mutableStateOf(settings?.sortAsc ?: true)
    /** 排序方向：true=升序 false=降序（写入 SettingsManager 持久化） */
    var sortAsc: Boolean
        get() = _sortAsc.value
        set(value) {
            _sortAsc.value = value
            settings?.sortAsc = value
        }
}

// ==================== 分类枚举 ====================

enum class MusicCategory { ALL, ARTIST, ALBUM }

@Composable
fun MusicCategory.displayName(): String = when (this) {
    MusicCategory.ALL -> stringResource(R.string.category_all)
    MusicCategory.ARTIST -> stringResource(R.string.category_artist)
    MusicCategory.ALBUM -> stringResource(R.string.category_album)
}

val LocalBrowserState = staticCompositionLocalOf { MusicBrowserState() }