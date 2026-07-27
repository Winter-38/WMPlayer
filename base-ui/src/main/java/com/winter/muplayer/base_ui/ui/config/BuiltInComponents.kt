package com.winter.muplayer.base_ui.ui.config

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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.winter.muplayer.base_ui.LocalBrowserState
import com.winter.muplayer.base_ui.MusicBrowserList
import com.winter.muplayer.base_ui.MusicBrowserSort
import com.winter.muplayer.base_ui.MusicBrowserTabs
import com.winter.muplayer.base_ui.R
import com.winter.muplayer.base_ui.ControlButton
import com.winter.muplayer.base_ui.PlayModeButton
import com.winter.muplayer.base_ui.PlayPauseButton
import com.winter.muplayer.base_ui.formatDuration
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState

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
        text = stringResource(com.winter.muplayer.base_ui.R.string.app_name),
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
    MusicBrowserTabs()
}

// ==================== sort ====================

@Composable
private fun SlotContext.Sort() {
    MusicBrowserSort()
}

// ==================== playlist ====================

@Composable
private fun SlotContext.Playlist() {
    Box(modifier = Modifier.fillMaxSize()) {
        MusicBrowserList(
            coverCache = coverCache,
            onTrackClick = { track, contextTracks ->
                onPlayTrackSmart(track, contextTracks)
            },
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面缩略图
            if (currentTrack != null) {
                com.winter.muplayer.base_ui.AlbumThumb(
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
                        painterResource(com.winter.muplayer.base_ui.R.drawable.ic_music_note),
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
                        ?: stringResource(com.winter.muplayer.base_ui.R.string.not_playing),
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
    val noTrack = stringResource(com.winter.muplayer.base_ui.R.string.no_track_selected)

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
                    painterResource(com.winter.muplayer.base_ui.R.drawable.ic_music_note),
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
                        contentDescription = stringResource(com.winter.muplayer.base_ui.R.string.playlist),
                        modifier = Modifier.size(32.dp),
                        tint = tint,
                    )
                }
            }
        }
    }
}
