package com.winter.muplayer.ui.components

import com.winter.muplayer.config.ComponentRegistry
import com.winter.muplayer.config.DataBinding
import com.winter.muplayer.config.LocalComponentCss
import com.winter.muplayer.config.LocalComponentExtra
import com.winter.muplayer.config.LocalProgress
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.isSlotHorizontal
import com.winter.muplayer.config.isSlotVertical
import com.winter.muplayer.config.parseCssColor
import com.winter.muplayer.config.parseCssDp
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.winter.muplayer.ui.browser.AlbumThumb
import com.winter.muplayer.ui.browser.getAlbumArtUri
import com.winter.muplayer.ui.browser.ItemStyle
import com.winter.muplayer.ui.browser.LocalBrowserState
import com.winter.muplayer.ui.browser.MusicBrowserList
import com.winter.muplayer.ui.browser.MusicCategory
import com.winter.muplayer.ui.browser.displayName
import android.os.Build
import android.graphics.Bitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import com.winter.muplayer.ui.R
import com.winter.muplayer.ui.components.ControlButton
import com.winter.muplayer.ui.components.PlayModeButton
import com.winter.muplayer.ui.components.PlayPauseButton
import com.winter.muplayer.ui.components.formatDuration
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        // 通用原子组件
        "icon" to { IconComponent() },
        "icon-button" to { IconButtonComponent() },
        "text" to { TextComponent() },
        "spacer" to { Spacer() },
        "cover" to { CoverComponent() },
        "progress-slider" to { ProgressSliderComponent() },
        // 播放控制原子按钮
        "play-pause-button" to { PlayPauseComponent() },
        "prev-button" to { PrevButtonComponent() },
        "next-button" to { NextButtonComponent() },
        "play-mode-button" to { PlayModeComponent() },
        "queue-button" to { QueueButtonComponent() },
        // 全屏播放器组件
        "track-info" to { TrackInfo() },
        "progress-bar" to { ProgressBar() },
        "controls-row" to { ControlsRow() },
        // 全屏播放器细分组件
        "fp-backdrop" to { FpBackdrop() },
        "fp-cover" to { FpCover() },
        "fp-track-title" to { FpTrackTitle() },
        "fp-track-subtitle" to { FpTrackSubtitle() },
        "fp-progress" to { FpProgress() },
    )
}

// ══════════════════════════════════════════════
// 内置组件实现
// ══════════════════════════════════════════════

@Composable
private fun SlotContext.AppName() {
    val css = LocalComponentCss.current
    val cssColor = css["color"]?.let { parseCssColor(it) }
    val fontSize = css["font-size"]?.let { parseCssDp(it) }
    Text(
        text = stringResource(com.winter.muplayer.ui.R.string.app_name),
        fontWeight = FontWeight.Bold,
        style = if (fontSize != null) {
            MaterialTheme.typography.titleLarge.copy(fontSize = fontSize.value.sp)
        } else {
            MaterialTheme.typography.titleLarge
        },
        color = cssColor ?: MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 图标按钮共享实现 —— 所有"图标 + 动作"按钮的公共渲染逻辑。
 * @param iconName drawable 资源名
 * @param action    点击行为标识，未识别时按钮不可点（渲染但无响应）
 */
@Composable
private fun SlotContext.iconButton(iconName: String, action: String) {
    val css = LocalComponentCss.current
    val iconSize = css["size"]?.let { parseCssDp(it) } ?: 40.dp
    val tint = css["color"]?.let { parseCssColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)

    // 显式类型变量：避免 when 中 lambda 分支 + 函数属性 + null 混用导致 Kotlin 推断歧义
    val togglePlay: () -> Unit = { if (playerState.state == PlayerState.PLAYING) onPause() else onPlay() }
    val onClick: (() -> Unit)? = when (action) {
        "toggleSearch", "openSearch" -> onOpenSearch
        "openSettings" -> onOpenSettings
        "previous" -> onPrevious
        "next" -> onNext
        "openQueue" -> onOpenQueue
        "openFullPlayer" -> onOpenFullPlayer
        "togglePlay" -> togglePlay
        else -> null
    }

    if (resId == 0 || onClick == null) {
        // 回退：彩色圆点，帮助排查配置问题（与 IconComponent 一致）
        Box(
            modifier = Modifier
                .size(iconSize * 0.6f)
                .background(tint, shape = androidx.compose.foundation.shape.CircleShape),
        )
        return
    }

    IconButton(onClick = onClick, modifier = Modifier.size(iconSize)) {
        Icon(
            painter = painterResource(resId),
            contentDescription = iconName,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            tint = tint,
        )
    }
}

/**
 * 通用图标按钮组件 —— `icon` 指定 drawable 资源名，`action` 指定点击行为。
 *
 * JSON 示例：
 *   { "icon-button": { "icon": "ic_search", "action": "toggleSearch" } }
 *
 * 支持 action：toggleSearch / openSettings / previous / next / openQueue / openFullPlayer / togglePlay
 * CSS 支持：color（tint）/ size（尺寸）
 */
@Composable
private fun SlotContext.IconButtonComponent() {
    val extra = LocalComponentExtra.current
    val iconName = extra["icon"] as? String ?: "ic_help"
    val action = extra["action"] as? String ?: ""
    iconButton(iconName, action)
}

@Composable
private fun SlotContext.SearchButton() {
    val extra = LocalComponentExtra.current
    iconButton(
        iconName = extra["icon"] as? String ?: "ic_search",
        action = extra["action"] as? String ?: "toggleSearch",
    )
}

@Composable
private fun SlotContext.SettingButton() {
    val extra = LocalComponentExtra.current
    iconButton(
        iconName = extra["icon"] as? String ?: "ic_settings",
        action = extra["action"] as? String ?: "openSettings",
    )
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
        modifier = Modifier.fillMaxSize(),
    )
}
 
// ==================== text ====================
 
/**
 * 通用文本组件 —— 通过 `extra["content"]` 指定显示内容。
 *
 * JSON 示例：
 *   { "text": { "content": "Hello World" } }
 *   { "#text@my-label": { "content": "我的标签" } }
 *
 * CSS 支持： `color` / `font-size` / `font-weight` / `font-style` / `text-align`
 *   #text { color: #ffffff; font-size: 16px; font-weight: bold; }
 */
@Composable
private fun SlotContext.TextComponent() {
    val extra = LocalComponentExtra.current
    val css = LocalComponentCss.current
    // bind 优先级高于 content：bind 引用播放器运行时状态（track.title / position 等）
    val bind = extra["bind"] as? String
    val content = if (bind != null) {
        DataBinding.resolve(bind, this, LocalProgress.current) ?: "(bind:$bind)"
    } else {
        extra["content"] as? String
    }

    val cssColor = css["color"]?.let { parseCssColor(it) }
    val fontSize = css["font-size"]?.let { parseCssDp(it) }
    val fontWeight = css["font-weight"]?.let { parseCssFontWeight(it) }
    val fontStyle = css["font-style"]?.let { parseCssFontStyle(it) }
    val textAlign = css["text-align"]?.let { parseCssTextAlign(it) }

    val baseStyle = MaterialTheme.typography.bodyMedium
    val style = baseStyle.copy(
        fontSize = fontSize?.let { it.value.sp } ?: baseStyle.fontSize,
        fontWeight = fontWeight ?: baseStyle.fontWeight,
        fontStyle = fontStyle ?: baseStyle.fontStyle,
        textAlign = textAlign ?: baseStyle.textAlign,
    )

    Text(
        text = content ?: "(text)",
        style = style,
        color = cssColor ?: MaterialTheme.colorScheme.onSurface,
        overflow = TextOverflow.Ellipsis,
        maxLines = 3,
    )
}

/** 解析 CSS font-weight 值 */
private fun parseCssFontWeight(value: String): FontWeight? = when (value.lowercase()) {
    "thin" -> FontWeight.Thin
    "extra-light", "extralight" -> FontWeight.ExtraLight
    "light" -> FontWeight.Light
    "normal" -> FontWeight.Normal
    "medium" -> FontWeight.Medium
    "semi-bold", "semibold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    "extra-bold", "extrabold" -> FontWeight.ExtraBold
    "black" -> FontWeight.Black
    else -> value.toIntOrNull()?.let { FontWeight(it) }
}

/** 解析 CSS font-style 值 */
private fun parseCssFontStyle(value: String): androidx.compose.ui.text.font.FontStyle? = when (value.lowercase()) {
    "italic" -> androidx.compose.ui.text.font.FontStyle.Italic
    "normal" -> androidx.compose.ui.text.font.FontStyle.Normal
    else -> null
}

/** 解析 CSS text-align 值 */
private fun parseCssTextAlign(value: String): TextAlign? = when (value.lowercase()) {
    "left" -> TextAlign.Start
    "center" -> TextAlign.Center
    "right" -> TextAlign.End
    else -> null
}
 
// ==================== spacer ====================

/**
 * 空白占位组件 —— 仅用于通过 CSS weight 占据空间，自身不可见。
 * 默认 CSS：
 *   #spacer { weight: 1; }
 */
@Composable
private fun SlotContext.Spacer() {
    // 空白占位 —— weight 由 LayoutRenderer 从 CSS 读取并应用为 Modifier.weight()
}

// ==================== cover ====================

/** 封面底层实现（传显式尺寸，供复合组件内部复用） */
@Composable
private fun SlotContext.cover(size: Dp) {
    AlbumThumb(albumTrack = playerState.currentTrack, coverCache = coverCache, size = size)
}

/**
 * 专辑封面缩略图 —— 显示当前播放曲目封面，无曲目/无封面时回退占位图标。
 * CSS 支持：size（尺寸）
 */
@Composable
private fun SlotContext.CoverComponent() {
    val css = LocalComponentCss.current
    val size = css["size"]?.let { parseCssDp(it) } ?: 48.dp
    cover(size)
}

// ==================== 播放控制原子按钮 ====================

/** 播放模式循环切换的共享逻辑（避免 controls-row 与 play-mode-button 各写一份） */
private fun nextPlayMode(mode: PlayMode): PlayMode = when (mode) {
    PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
    PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
    PlayMode.SINGLE_LOOP -> PlayMode.REPEAT_ALL
    PlayMode.REPEAT_ALL -> PlayMode.SEQUENTIAL
}

/**
 * 播放/暂停按钮原子组件。
 * CSS 支持：color（tint）
 */
@Composable
private fun SlotContext.PlayPauseComponent() {
    val css = LocalComponentCss.current
    val tint = css["color"]?.let { parseCssColor(it) } ?: MaterialTheme.colorScheme.onSurface
    PlayPauseButton(
        isPlaying = playerState.state == PlayerState.PLAYING,
        isLoading = playerState.state == PlayerState.LOADING,
        onPlay = onPlay,
        onPause = onPause,
        containerColor = tint.copy(alpha = 0.2f),
        iconTint = tint,
    )
}

/** 上一首按钮原子组件。CSS 支持：color（tint）/ size（尺寸） */
@Composable
private fun SlotContext.PrevButtonComponent() {
    val css = LocalComponentCss.current
    val tint = css["color"]?.let { parseCssColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val size = css["size"]?.let { parseCssDp(it) } ?: 48.dp
    ControlButton(
        icon = painterResource(R.drawable.ic_skip_previous),
        onClick = onPrevious,
        size = size,
        tint = tint,
    )
}

/** 下一首按钮原子组件。CSS 支持：color（tint）/ size（尺寸） */
@Composable
private fun SlotContext.NextButtonComponent() {
    val css = LocalComponentCss.current
    val tint = css["color"]?.let { parseCssColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val size = css["size"]?.let { parseCssDp(it) } ?: 48.dp
    ControlButton(
        icon = painterResource(R.drawable.ic_skip_next),
        onClick = onNext,
        size = size,
        tint = tint,
    )
}

/** 播放模式按钮原子组件。CSS 支持：color（tint） */
@Composable
private fun SlotContext.PlayModeComponent() {
    val css = LocalComponentCss.current
    val tint = css["color"]?.let { parseCssColor(it) } ?: MaterialTheme.colorScheme.onSurface
    PlayModeButton(
        playMode = playMode,
        onClick = { onPlayModeChange(nextPlayMode(playMode)) },
        tint = tint,
    )
}

/** 播放队列按钮原子组件。CSS 支持：color（tint）/ size（尺寸） */
@Composable
private fun SlotContext.QueueButtonComponent() {
    val css = LocalComponentCss.current
    val tint = css["color"]?.let { parseCssColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val size = css["size"]?.let { parseCssDp(it) } ?: 32.dp
    IconButton(onClick = onOpenQueue) {
        Icon(
            painter = painterResource(R.drawable.ic_playlist_music),
            contentDescription = stringResource(R.string.playlist),
            modifier = Modifier.size(size),
            tint = tint,
        )
    }
}

// ==================== progress-slider ====================

/** 进度滑块底层实现（复合组件 progress-bar 内部复用，避免 Slider 样式重复） */
@Composable
private fun SlotContext.progressSlider(modifier: Modifier = Modifier) {
    val progressData = LocalProgress.current
    Slider(
        value = if (progressData.duration > 0)
            progressData.progress.toFloat() / progressData.duration.toFloat()
        else 0f,
        onValueChange = { fraction ->
            onSeek((fraction * progressData.duration).toLong())
        },
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        ),
    )
}

/**
 * 进度条滑块原子组件 —— 订阅 LocalProgress，拖动时回调 onSeek。
 * 与时间标签（text + bind position/duration）组合即可拼出完整进度条。
 */
@Composable
private fun SlotContext.ProgressSliderComponent() {
    progressSlider(Modifier.fillMaxWidth())
}

// ==================== tab-bar ====================

/**
 * Tab 栏组件。CSS 属性 `display` 控制布局：
 * - `display: column` → 竖向 FilterChip 堆叠
 * - 缺省或其他值       → 横向 PrimaryTabRow 标签栏
 */
@Composable
private fun SlotContext.TabBar() {
    val state = LocalBrowserState.current
    val css = LocalComponentCss.current
    if (css["display"] == "column") {
        // 竖向堆叠（FilterChip）
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
    } else {
        // 横向标签栏（PrimaryTabRow）
        PrimaryTabRow(
            selectedTabIndex = state.selectedCategory.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            MusicCategory.entries.forEach { category ->
                Tab(
                    selected = state.selectedCategory == category,
                    onClick = { state.selectedCategory = category },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (category) {
                                MusicCategory.ALL -> R.drawable.ic_library_music
                                MusicCategory.ARTIST -> R.drawable.ic_person
                                MusicCategory.ALBUM -> R.drawable.ic_disc
                            }
                            Icon(
                                painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(category.displayName())
                        }
                    }
                )
            }
        }
    }
}

// ==================== sort ====================

@Composable
private fun SlotContext.Sort() {
    val state = LocalBrowserState.current
    val sortNames = listOf(
        stringResource(R.string.sort_name),
        stringResource(R.string.sort_duration),
        stringResource(R.string.sort_file_size),
        stringResource(R.string.sort_date_added),
        stringResource(R.string.sort_file_type)
    )
    var showSortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
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
            modifier = Modifier.weight(1f)
        )
        Box {
            Text(
                text = stringResource(R.string.sort_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { showSortMenu = true }
            )
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                sortNames.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = { Text(name, fontWeight = if (state.sortField == i) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { state.sortField = i; showSortMenu = false },
                        trailingIcon = { if (state.sortField == i) Text("✓", fontWeight = FontWeight.Bold) }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (state.sortAsc) stringResource(R.string.sort_asc) else stringResource(R.string.sort_desc), fontWeight = FontWeight.Bold) },
                    onClick = { state.sortAsc = !state.sortAsc; showSortMenu = false }
                )
            }
        }
    }
}

// ==================== playlist ====================

/** 从 #playlist 组件 CSS 解析条目样式（item-* 属性），未设置的项保持 null（使用主题默认） */
private fun parseItemStyle(css: Map<String, String>): ItemStyle = ItemStyle(
    background = css["item-bg"]?.let { parseCssColor(it) }
        ?: css["item-background"]?.let { parseCssColor(it) },
    radius = css["item-radius"]?.let { parseCssDp(it) },
    titleColor = css["item-color"]?.let { parseCssColor(it) }
        ?: css["item-text-color"]?.let { parseCssColor(it) },
    titleSize = css["item-font-size"]?.let { parseCssDp(it) }
        ?.takeIf { it.value > 0f }?.value?.sp,
    subColor = css["item-sub-color"]?.let { parseCssColor(it) },
    subSize = css["item-sub-size"]?.let { parseCssDp(it) }
        ?.takeIf { it.value > 0f }?.value?.sp,
    fontFamily = css["item-font-family"]?.let { parseFontFamily(it) },
)

/** 解析字体族：serif / monospace / cursive，sans-serif 或无效值返回 null（主题默认） */
private fun parseFontFamily(value: String): FontFamily? = when (value.trim().lowercase()) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotContext.Playlist() {
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val appContext = LocalContext.current
    val browserState = LocalBrowserState.current
    val css = LocalComponentCss.current
    val itemStyle = remember(css) { parseItemStyle(css) }

    Box(modifier = Modifier.fillMaxSize()) {
        MusicBrowserList(
            coverCache = coverCache,
            itemStyle = itemStyle,
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

                // 添加到播放列表（无队列→直接播放单曲；有队列→插入列表顶部立即播放）
                TextButton(
                    onClick = {
                        musicPlayerCore.playTrackTop(track)
                        selectedTrack = null
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
                cover(80.dp)

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
            cover(56.dp)

            // 封面与歌曲信息的间隙
            Spacer(Modifier.width(12.dp))

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

// ==================== 封面模糊背景层（容器组件） ====================
// children 中声明的子 slots 由 SlotRenderer 以 Box 叠层渲染在背景之上。

@Composable
private fun SlotContext.FpBackdrop() {
    val currentTrack = playerState.currentTrack
    val context = LocalContext.current
    var coverBitmap by remember(currentTrack?.id, coverCache) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(currentTrack?.id, coverCache) {
        if (blurBackground && currentTrack != null) {
            val uri = getAlbumArtUri(currentTrack, coverCache)
            if (uri != null) {
                try {
                    val loader = coil.ImageLoader(context)
                    val result = loader.execute(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(100, 100)
                            .crossfade(false)
                            .build()
                    )
                    val drawable = result.drawable
                    coverBitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } catch (_: Exception) {
                    coverBitmap = null
                }
            } else {
                coverBitmap = null
            }
        } else {
            coverBitmap = null
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (coverBitmap != null) {
            Crossfade(
                targetState = coverBitmap,
                animationSpec = tween(durationMillis = 400),
                label = "fpBackdrop"
            ) { bmp ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bmp)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 48.dp)
                )
            }
        }
    }
}

// ==================== 主封面 ====================

@Composable
private fun SlotContext.FpCover() {
    val currentTrack = playerState.currentTrack
    val context = LocalContext.current
    // 切歌时保持旧封面直到新封面加载完成（与全屏面板原逻辑一致）
    var coverState by remember { mutableStateOf<Pair<Long, Any?>?>(null) }
    var lastCoverUri by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(currentTrack?.id, coverCache) {
        val track = currentTrack
        if (track == null) {
            coverState = null
            lastCoverUri = null
            return@LaunchedEffect
        }
        val uri: Any? = getAlbumArtUri(track, coverCache)
        if (coverState?.first == track.id && lastCoverUri == uri) return@LaunchedEffect
        val loaded = if (uri != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(Size.ORIGINAL)
                            .build()
                    ).drawable != null
                } catch (_: Exception) {
                    false
                }
            }
        } else false
        lastCoverUri = uri
        coverState = track.id to (if (loaded) uri else null)
    }
    val displayedCover: Any? = coverState?.second
    val albumArtCorner = 24.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(albumArtCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shadow(16.dp, RoundedCornerShape(albumArtCorner)),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = displayedCover,
            animationSpec = tween(durationMillis = 400),
            label = "fpCover"
        ) { cover ->
            if (cover != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(cover)
                        .size(Size.ORIGINAL)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painterResource(com.winter.muplayer.ui.R.drawable.ic_music_off),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ==================== 歌曲标题 ====================

@Composable
private fun SlotContext.FpTrackTitle() {
    val currentTrack = playerState.currentTrack
    val tint = if (adaptiveTint != Color.Unspecified) adaptiveTint else MaterialTheme.colorScheme.onSurface
    Text(
        text = currentTrack?.title ?: stringResource(com.winter.muplayer.ui.R.string.no_track_selected),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = tint,
    )
}

// ==================== 歌曲艺人 + 专辑 ====================

@Composable
private fun SlotContext.FpTrackSubtitle() {
    val currentTrack = playerState.currentTrack
    val tint = if (adaptiveTint != Color.Unspecified) adaptiveTint else MaterialTheme.colorScheme.onSurface
    if (currentTrack != null) {
        Text(
            text = "${currentTrack.artist} • ${currentTrack.album}",
            style = MaterialTheme.typography.bodyLarge,
            color = tint.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ==================== 进度条 + 时间显示（合并） ====================

@Composable
private fun SlotContext.FpProgress() {
    val progressData = LocalProgress.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (progressData.duration > 0)
                progressData.progress.toFloat() / progressData.duration.toFloat()
            else 0f,
            onValueChange = { fraction ->
                onSeek((fraction * progressData.duration).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(progressData.progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(progressData.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
    val progressData = LocalProgress.current
    if (isSlotHorizontal) {
        // 横向父 slot → 行内紧凑进度条
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(progressData.progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            progressSlider(Modifier.weight(1f).padding(horizontal = 4.dp))
            Text(
                text = formatDuration(progressData.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val w = maxWidth
        Column(modifier = Modifier.fillMaxWidth()) {
            progressSlider(Modifier.fillMaxWidth())

            // 窄时隐藏时间标签
            if (w >= 180.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration(progressData.progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(progressData.duration),
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
                onPlayModeChange(nextPlayMode(playMode))
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
                onClick = { onPlayModeChange(nextPlayMode(playMode)) },
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