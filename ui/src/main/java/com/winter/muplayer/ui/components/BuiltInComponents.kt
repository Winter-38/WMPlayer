package com.winter.muplayer.ui.components

import com.winter.muplayer.config.ComponentRegistry
import com.winter.muplayer.config.LocalComponentCss
import com.winter.muplayer.config.LocalComponentExtra
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.isSlotHorizontal
import com.winter.muplayer.config.isSlotVertical
import com.winter.muplayer.config.parseCssColor
import com.winter.muplayer.config.parseCssDp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.winter.muplayer.ui.browser.LocalBrowserState
import com.winter.muplayer.ui.browser.MusicBrowserList
import com.winter.muplayer.ui.browser.MusicBrowserSort
import com.winter.muplayer.ui.browser.MusicBrowserTabs
import com.winter.muplayer.ui.browser.MusicCategory
import com.winter.muplayer.ui.browser.displayName
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import com.winter.muplayer.ui.R
import com.winter.muplayer.ui.components.ControlButton
import com.winter.muplayer.ui.components.PlayModeButton
import com.winter.muplayer.ui.components.PlayPauseButton
import com.winter.muplayer.ui.components.formatDuration
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.Track

/**
 * 注册所有内置组件到 ComponentRegistry。
 * 调用此函数后，JSON 布局中 #app-name / #search-button / #search-bar / #playlist / #playbar
 * 等均可渲染。
 */
fun registerBuiltInComponents() {
    ComponentRegistry.registerAll(
        "app-name" to { AppName() },
        "search-button" to { SearchButton() },
        "setting-button" to { SettingButton() },
        "tab-bar" to { TabBar() },
        "sort" to { Sort() },
        "playlist" to { Playlist() },
        "playbar" to { PlayBar() },
        "icon" to { IconComponent() },
        // 全屏播放器组件
        "track-info" to { TrackInfo() },
        "progress-bar" to { ProgressBar() },
        "controls-row" to { ControlsRow() },
        // 向后兼容：旧版 playlist 复合组件
        "old-playlist" to { OldPlaylist() },
    )
}

// ══════════════════════════════════════════════
// 内置组件实现
// ══════════════════════════════════════════════

@Composable
private fun SlotContext.AppName() {
    val cssColor = LocalComponentCss.current["color"]?.let { parseCssColor(it) }
    Text(
        text = stringResource(com.winter.muplayer.ui.R.string.app_name),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge,
        color = cssColor ?: MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SlotContext.SearchButton() {
    IconButton(onClick = onOpenSearch) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = stringResource(R.string.search),
        )
    }
}

@Composable
private fun SlotContext.SettingButton() {
    IconButton(onClick = onOpenSettings) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.settings),
        )
    }
}

/**
 * 通用图标组件 —— `icon` 强制为 string，只接受 drawable 资源名（如 "ic_search"）。
 *
 * 不支持图片路径/URL/Base64，避免高分辨率图像导致的性能开销。
 * 找不到图标时渲染一个彩色圆点作为回退标记，帮助排查配置问题。
 *
 * JSON 示例：
 *   { "type": "#icon", "icon": "ic_search" }
 *
 * CSS 支持： `color`（tint） / `size`（尺寸）
 *   #icon { color: #ff6b6b; size: 24px; }
 */
@Composable
private fun SlotContext.IconComponent() {
    val extra = LocalComponentExtra.current
    val css = LocalComponentCss.current
    val iconName = extra["icon"] as? String

    val tintColor = css["color"]?.let { parseCssColor(it) }
        ?: MaterialTheme.colorScheme.onSurface
    val iconSize = css["size"]?.let { parseCssDp(it) } ?: 24.dp

    // 回退：找不到图标时渲染一个彩色圆点
    val fallback: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(iconSize * 0.6f)
                .background(tintColor, shape = androidx.compose.foundation.shape.CircleShape),
        )
    }

    if (iconName == null) {
        android.util.Log.w("IconComponent", "Missing 'icon' in extra — rendering fallback dot")
        fallback()
        return
    }

    val context = LocalContext.current
    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
    if (resId == 0) {
        android.util.Log.w("IconComponent", "Drawable not found: $iconName — rendering fallback dot")
        fallback()
        return
    }

    Icon(
        painter = painterResource(resId),
        contentDescription = iconName,
        tint = tintColor,
        modifier = Modifier.size(iconSize),
    )
}

// ==================== tab-bar ====================

@Composable
private fun SlotContext.TabBar() {
    if (isSlotVertical) {
        // 竖向父 slot → FilterChip 垂直排列
        val state = LocalBrowserState.current
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MusicCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = { state.selectedCategory = category },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (category) {
                                MusicCategory.ALL -> R.drawable.ic_library_music
                                MusicCategory.ARTIST -> R.drawable.ic_person
                                MusicCategory.ALBUM -> R.drawable.ic_disc
                            }
                            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(category.displayName())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }
    MusicBrowserTabs()
}

// ==================== sort ====================

@Composable
private fun SlotContext.Sort() {
    if (isSlotVertical) {
        // 竖向父 slot → Column 垂直排列
        val state = LocalBrowserState.current
        val sortNames = listOf(
            stringResource(R.string.sort_name),
            stringResource(R.string.sort_duration),
            stringResource(R.string.sort_file_size),
            stringResource(R.string.sort_date_added),
            stringResource(R.string.sort_file_type)
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val summaryText = when (state.selectedCategory) {
                MusicCategory.ALL -> stringResource(R.string.track_count, state.tracks.size)
                MusicCategory.ARTIST -> {
                    val groups = state.tracks.groupBy { it.artist.ifBlank { stringResource(com.winter.muplayer.ui.R.string.unknown_artist) } }
                    stringResource(R.string.artist_count, groups.size)
                }
                MusicCategory.ALBUM -> {
                    val groups = state.tracks.groupBy { it.album.ifBlank { stringResource(com.winter.muplayer.ui.R.string.unknown_album) } }
                    stringResource(R.string.album_count, groups.size)
                }
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sortNames.forEachIndexed { index, name ->
                FilterChip(
                    selected = state.sortField == index,
                    onClick = {
                        if (state.sortField == index) {
                            state.sortAsc = !state.sortAsc
                        } else {
                            state.sortField = index
                            state.sortAsc = true
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sortNames[index])
                            if (state.sortField == index) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (state.sortAsc) "▲" else "▼",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }
    MusicBrowserSort()
}

// ==================== playlist ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotContext.Playlist() {
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current
    val browserState = LocalBrowserState.current

    Box(modifier = Modifier.fillMaxSize()) {
        MusicBrowserList(
            coverCache = coverCache,
            onTrackClick = { track, contextTracks ->
                onPlayTrackSmart(track, contextTracks)
            },
            onTrackLongClick = { track ->
                selectedTrack = track
            }
        )
    }

    // ── 长按菜单 ──
    if (selectedTrack != null) {
        val track = selectedTrack!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedTrack = null },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                // 标题
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()

                // 添加到播放列表
                TextButton(
                    onClick = {
                        showPlaylistPicker = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playlist_add),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.add_to_playlist),
                        modifier = Modifier.weight(1f)
                    )
                }

                // 删除歌曲
                TextButton(
                    onClick = {
                        showDeleteConfirm = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.delete_song),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // ── 播放列选选择器 ──
    if (showPlaylistPicker && selectedTrack != null) {
        val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            sheetState = pickerSheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.add_to_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                HorizontalDivider()

                val playlists by musicPlayerCore.playlistManager.playlists.collectAsState()
                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_playlists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    playlists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    musicPlayerCore.playlistManager.addTrack(playlist.id, selectedTrack!!.id)
                                }
                                showPlaylistPicker = false
                                selectedTrack = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_library_music),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(playlist.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // ── 删除确认 ──
    if (showDeleteConfirm && selectedTrack != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_song)) },
            text = { Text(stringResource(R.string.delete_song_confirm, selectedTrack!!.title)) },
            confirmButton = {
                TextButton(onClick = {
                    val trackToDelete = selectedTrack!!
                    // 从 MediaStore 删除（API 29+）
                    if (Build.VERSION.SDK_INT >= 29 && trackToDelete.id > 0L) {
                        try {
                            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            appContext.contentResolver.delete(
                                android.net.Uri.withAppendedPath(uri, trackToDelete.id.toString()),
                                null, null
                            )
                        } catch (_: Exception) { }
                    }
                    // 从界面列表移除
                    browserState.tracks = browserState.tracks.filter { it.id != trackToDelete.id }
                    // 清除缓存
                    musicPlayerCore.musicIndexCache.invalidate()
                    // 关闭菜单
                    showDeleteConfirm = false
                    selectedTrack = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ==================== 向后兼容的旧版 playlist 复合组件 ====================

@Composable
private fun SlotContext.OldPlaylist() {
    Column(modifier = Modifier.fillMaxSize()) {
        MusicBrowserTabs()
        MusicBrowserSort()
        Box(modifier = Modifier.weight(1f)) {
            MusicBrowserList(
                coverCache = coverCache,
                onTrackClick = { track, contextTracks ->
                    onPlayTrackSmart(track, contextTracks)
                },
            )
        }
    }
}

@Composable
private fun SlotContext.PlayBar() {
    val currentTrack = playerState.currentTrack
    val isPlaying = playerState.state == PlayerState.PLAYING

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onOpenFullPlayer,
    ) {
        if (isSlotVertical) {
            // 竖向父 slot → 垂直堆叠：封面→信息→控制
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 封面缩略图
                if (currentTrack != null) {
                    com.winter.muplayer.ui.browser.AlbumThumb(
                        albumTrack = currentTrack,
                        coverCache = coverCache,
                        size = 80.dp,
                    )
                } else {
                    Icon(
                        painterResource(com.winter.muplayer.ui.R.drawable.ic_music_note),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 歌曲信息
                Text(
                    text = currentTrack?.title ?: stringResource(com.winter.muplayer.ui.R.string.not_playing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (currentTrack != null) {
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 控制按钮
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = stringResource(R.string.previous), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                    }
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 2.dp,
                    ) {
                        IconButton(onClick = { if (isPlaying) onPause() else onPlay() }, modifier = Modifier.fillMaxSize()) {
                            Icon(painter = if (isPlaying) painterResource(R.drawable.ic_pause) else painterResource(R.drawable.ic_play),
                                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                        }
                    }
                    IconButton(onClick = onNext) {
                        Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.next), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(painterResource(R.drawable.ic_playlist_music), contentDescription = stringResource(R.string.playlist), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
                    }
                }
            }
            return@Surface
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面缩略图
            if (currentTrack != null) {
                com.winter.muplayer.ui.browser.AlbumThumb(
                    albumTrack = currentTrack,
                    coverCache = coverCache,
                    size = 56.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(com.winter.muplayer.ui.R.drawable.ic_music_note),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title
                        ?: stringResource(com.winter.muplayer.ui.R.string.not_playing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (currentTrack != null) {
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            // 上一首
            IconButton(onClick = onPrevious) {
                Icon(
                    painterResource(R.drawable.ic_skip_previous),
                    contentDescription = stringResource(R.string.previous),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }

            // 播放/暂停
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp,
            ) {
                IconButton(
                    onClick = { if (isPlaying) onPause() else onPlay() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = if (isPlaying) painterResource(R.drawable.ic_pause)
                        else painterResource(R.drawable.ic_play),
                        contentDescription = if (isPlaying) stringResource(R.string.pause)
                        else stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // 下一首
            IconButton(onClick = onNext) {
                Icon(
                    painterResource(R.drawable.ic_skip_next),
                    contentDescription = stringResource(R.string.next),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }

            // 播放列表按钮
            IconButton(onClick = onOpenQueue) {
                Icon(
                    painterResource(R.drawable.ic_playlist_music),
                    contentDescription = stringResource(R.string.playlist),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

// ══════════════════════════════════════════════
// 全屏播放器组件
// ══════════════════════════════════════════════

@Composable
private fun SlotContext.TrackInfo() {
    val currentTrack = playerState.currentTrack
    val tint = if (adaptiveTint != Color.Unspecified) adaptiveTint else MaterialTheme.colorScheme.onSurface
    val noTrack = stringResource(com.winter.muplayer.ui.R.string.no_track_selected)

    if (isSlotHorizontal) {
        // 横向父 slot → 紧凑单行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = if (currentTrack != null)
                "${currentTrack.title} — ${currentTrack.artist}"
            else noTrack
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = tint,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val w = maxWidth
        when {
            w >= 200.dp -> {
                Column {
                    Text(
                        text = currentTrack?.title ?: noTrack,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = tint,
                    )
                    if (currentTrack != null) {
                        Text(
                            text = "${currentTrack.artist} • ${currentTrack.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tint.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            w >= 120.dp -> {
                val label = if (currentTrack != null)
                    "${currentTrack.title} — ${currentTrack.artist}"
                else noTrack
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = tint,
                )
            }
            else -> {
                Icon(
                    painterResource(com.winter.muplayer.ui.R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SlotContext.ProgressBar() {
    if (isSlotHorizontal) {
        // 横向父 slot → 行内紧凑进度条
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(playerState.progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = if (playerState.duration > 0)
                    playerState.progress.toFloat() / playerState.duration.toFloat()
                else 0f,
                onValueChange = { fraction ->
                    onSeek((fraction * playerState.duration).toLong())
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                ),
            )
            Text(
                text = formatDuration(playerState.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val w = maxWidth
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = if (playerState.duration > 0)
                    playerState.progress.toFloat() / playerState.duration.toFloat()
                else 0f,
                onValueChange = { fraction ->
                    onSeek((fraction * playerState.duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                ),
            )

            // 窄时隐藏时间标签
            if (w >= 180.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration(playerState.progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(playerState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotContext.ControlsRow() {
    val isPlaying = playerState.state == PlayerState.PLAYING
    val tint = if (adaptiveTint != Color.Unspecified) adaptiveTint else MaterialTheme.colorScheme.onSurface

    if (isSlotVertical) {
        // 竖向父 slot → 按钮垂直居中堆叠
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayModeButton(playMode = playMode, onClick = {
                val newMode = when (playMode) {
                    PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
                    PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
                    PlayMode.SINGLE_LOOP -> PlayMode.REPEAT_ALL
                    PlayMode.REPEAT_ALL -> PlayMode.SEQUENTIAL
                }
                onPlayModeChange(newMode)
            }, tint = tint)
            PlayPauseButton(
                isPlaying = isPlaying,
                isLoading = playerState.state == PlayerState.LOADING,
                onPlay = onPlay, onPause = onPause,
                containerColor = tint.copy(alpha = 0.2f), iconTint = tint,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(icon = painterResource(R.drawable.ic_skip_previous), onClick = onPrevious, size = 40.dp, tint = tint)
                ControlButton(icon = painterResource(R.drawable.ic_skip_next), onClick = onNext, size = 40.dp, tint = tint)
                IconButton(onClick = onOpenQueue) {
                    Icon(painterResource(R.drawable.ic_playlist_music),
                        contentDescription = stringResource(com.winter.muplayer.ui.R.string.playlist),
                        modifier = Modifier.size(28.dp), tint = tint)
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val w = maxWidth
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 播放模式
            PlayModeButton(
                playMode = playMode,
                onClick = {
                    val newMode = when (playMode) {
                        PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
                        PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
                        PlayMode.SINGLE_LOOP -> PlayMode.REPEAT_ALL
                        PlayMode.REPEAT_ALL -> PlayMode.SEQUENTIAL
                    }
                    onPlayModeChange(newMode)
                },
                tint = tint,
            )

            // 上一首
            if (w >= 160.dp) {
                ControlButton(
                    icon = painterResource(R.drawable.ic_skip_previous),
                    onClick = onPrevious,
                    size = 48.dp,
                    tint = tint,
                )
            }

            // 播放/暂停
            PlayPauseButton(
                isPlaying = isPlaying,
                isLoading = playerState.state == PlayerState.LOADING,
                onPlay = onPlay,
                onPause = onPause,
                containerColor = tint.copy(alpha = 0.2f),
                iconTint = tint,
            )

            // 下一首
            if (w >= 160.dp) {
                ControlButton(
                    icon = painterResource(R.drawable.ic_skip_next),
                    onClick = onNext,
                    size = 48.dp,
                    tint = tint,
                )
            }

            // 播放列表
            if (w >= 200.dp) {
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        painterResource(R.drawable.ic_playlist_music),
                        contentDescription = stringResource(com.winter.muplayer.ui.R.string.playlist),
                        modifier = Modifier.size(32.dp),
                        tint = tint,
                    )
                }
            }
        }
    }
}
