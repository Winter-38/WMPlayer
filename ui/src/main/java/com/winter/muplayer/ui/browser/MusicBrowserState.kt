package com.winter.muplayer.ui.browser

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
) {
    var tracks by mutableStateOf(initialTracks)
    var isLoading by mutableStateOf(true)
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf(MusicCategory.ALL)
    var sortField by mutableIntStateOf(0)
    var sortAsc by mutableStateOf(true)
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
