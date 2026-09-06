/*
 * 本文件包含基于 Kyant0/AndroidLiquidGlass（Apache License 2.0，Copyright 2025 Kyant）
 * catalog 示例实现的液态玻璃渲染效果（liquidGlassSurface 及配套参数解析），
 * 已针对迷你播放栏适配修改；归属声明见仓库 THIRD_PARTY_NOTICES.md。
 * 本文件其余部分遵循项目 MIT License。
 */
package com.winter.muplayer.ui.components

import com.winter.muplayer.config.ComponentRegistry
import com.winter.muplayer.config.DataBinding
import com.winter.muplayer.config.LocalComponentCss
import com.winter.muplayer.config.LocalComponentExtra
import com.winter.muplayer.config.LocalGlassBackdrop
import com.winter.muplayer.config.LocalProgress
import com.winter.muplayer.config.LiquidGlassBackdrop
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.isSlotHorizontal
import com.winter.muplayer.config.isSlotVertical
import com.winter.muplayer.config.parseCssColor
import com.winter.muplayer.config.parseCssDp
import com.winter.muplayer.config.parseCssNumber
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.winter.muplayer.ui.browser.AlbumThumb
import com.winter.muplayer.ui.browser.getAlbumArtUri
import com.winter.muplayer.ui.browser.ItemLayout
import com.winter.muplayer.ui.browser.ItemStyle
import com.winter.muplayer.ui.browser.LocalBrowserState
import com.winter.muplayer.ui.browser.MusicBrowserList
import com.winter.muplayer.ui.browser.MusicCategory
import com.winter.muplayer.ui.browser.displayName
import android.os.Build
import android.graphics.Bitmap
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
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

/** 颜色变暗：factor ∈ (0, 1)，越小越暗；alpha 保持不变 */
private fun darker(color: Color, factor: Float): Color =
    Color(
        red = color.red * factor,
        green = color.green * factor,
        blue = color.blue * factor,
        alpha = color.alpha,
    )

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
        "track-count" to { TrackCountSummary() },
        "playlist" to { Playlist() },
        "playbar" to { PlayBar() },
        // 迷你播放栏细分组件（pb-* 系列，可单独在 JSON 中引用）
        "pb-backdrop" to { PbBackdrop() },
        "pb-cover" to { PbCoverComponent() },
        "pb-title" to { PbTitleComponent() },
        "pb-subtitle" to { PbSubtitleComponent() },
        "pb-track-info" to { PbTrackInfoComponent() },
        "pb-controls" to { PbControlsComponent() },
        // 通用原子组件
        "icon" to { IconComponent() },
        "icon-button" to { IconButtonComponent() },
        "text" to { TextComponent() },
        "rect" to { RectComponent() },
        "spacer" to { Spacer() },
        "cover" to { CoverComponent() },
        "progress-slider" to { ProgressSliderComponent() },
        // 播放控制原子按钮
        "play-button" to { PlayPauseComponent() },
        "prev-button" to { PrevButtonComponent() },
        "next-button" to { NextButtonComponent() },
        "playmode-button" to { PlayModeComponent() },
        "queue-button" to { QueueButtonComponent() },
        // 全屏播放器组件
        "track-info" to { TrackInfo() },
        "progress-bar" to { ProgressBar() },
        "controls-row" to { ControlsRow() },
        // 全屏播放器细分组件
        "fp-backdrop" to { FpBackdrop() },
        "fp-cover" to { FpCover() },
        "fp-title" to { FpTrackTitle() },
        "fp-subtitle" to { FpTrackSubtitle() },
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
        // 应用名与屏幕左边缘之间留出空隙（M3 横向边距惯例）
        modifier = Modifier.padding(start = 16.dp),
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

    val burst = rememberParticleBurstState()
    ParticleBurstBox(
        state = burst,
        modifier = Modifier.size(iconSize),
        color = tint,
    ) {
        IconButton(
            onClick = {
                burst.burst()
                onClick()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                painter = painterResource(resId),
                contentDescription = iconName,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                tint = tint,
            )
        }
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
 * 通用文本组件 —— 通过 `extra["content"]` 或 CSS `content` 属性指定显示内容。
 *
 * JSON 示例：
 *   { "text": { "content": "Hello World" } }
 *   { "#text@my-label": { "content": "我的标签" } }
 *
 * CSS 支持： `content`（字符串，文本内容）/ `color` / `font-size` / `font-weight` / `font-style` / `text-align`
 *   #text { content: "Hello"; color: #ffffff; font-size: 16px; font-weight: bold; }
 *
 * 内容优先级：bind（运行时状态） > CSS content > JSON content。
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
        // CSS content（去引号）优先，回退 JSON content
        css["content"]?.let { unquoteCssString(it) } ?: extra["content"] as? String
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

/**
 * 矩形色块组件 —— 纯展示色块（无内容/无手势）。
 *
 * JSON 示例：
 *   { "rect": {} }   /   "rect"
 *
 * CSS 支持： `color`（填充色）/ `size`（边长，默认 48px）/ `border-radius`（默认 0，纯矩形）
 *   #rect-demo { color: #F44336; size: 40px; border-radius: 8px; }
 */
@Composable
private fun SlotContext.RectComponent() {
    val css = LocalComponentCss.current
    val fill = css["color"]?.let { parseCssColor(it) }
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val size = css["size"]?.let { parseCssDp(it) } ?: 48.dp
    val radius = css["border-radius"]?.let { parseCssDp(it) } ?: 0.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(fill)
            .renderedColor(fill),
    )
}

/** 去除 CSS 字符串值首尾引号（支持单/双引号）。 */
private fun unquoteCssString(value: String): String {
    val t = value.trim()
    if (t.length >= 2) {
        val first = t.first()
        val last = t.last()
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return t.substring(1, t.length - 1)
        }
    }
    return t
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

/** 封面底层实现（传显式尺寸，供复合组件内部复用）—— 显示即按需缓存当前播放封面 */
@Composable
private fun SlotContext.cover(size: Dp) {
    val track = playerState.currentTrack
    val context = LocalContext.current
    LaunchedEffect(track?.id) {
        if (track != null) withContext(Dispatchers.IO) { cacheCoverFile(context, track, coverCache) }
    }
    AlbumThumb(albumTrack = track, coverCache = coverCache, size = size)
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

/** 播放模式循环切换的共享逻辑（避免 controls-row 与 playmode-button 各写一份） */
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
    val tint = css["color"]?.let { parseCssColor(it) }
        ?: adaptiveTint.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurface
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
    val tint = css["color"]?.let { parseCssColor(it) }
        ?: adaptiveTint.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurface
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
    val tint = css["color"]?.let { parseCssColor(it) }
        ?: adaptiveTint.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurface
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
    val tint = css["color"]?.let { parseCssColor(it) }
        ?: adaptiveTint.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurface
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
    val tint = css["color"]?.let { parseCssColor(it) }
        ?: adaptiveTint.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.onSurface
    val size = css["size"]?.let { parseCssDp(it) } ?: 32.dp
    val burst = rememberParticleBurstState()
    ParticleBurstBox(
        state = burst,
        modifier = Modifier.size(size),
        color = tint,
    ) {
        IconButton(
            onClick = {
                burst.burst()
                onOpenQueue()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_playlist_music),
                contentDescription = stringResource(R.string.playlist),
                modifier = Modifier.size(size),
                tint = tint,
            )
        }
    }
}

// ==================== progress-slider ====================

/** 进度滑块底层实现（复合组件 progress-bar 内部复用，避免 Slider 样式重复） */
@Composable
private fun SlotContext.progressSlider(modifier: Modifier = Modifier) {
    val progressData = LocalProgress.current
    val duration = progressData.duration
    val playFraction = if (duration > 0)
        progressData.progress.toFloat() / duration.toFloat()
    else 0f
    val anim = rememberProgressSliderAnim(playFraction, duration) { fraction ->
        onSeek((fraction * duration).toLong())
    }
    Slider(
        value = anim.value,
        onValueChange = anim.onDrag,
        onValueChangeFinished = anim.onDragEnd,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        ),
    )
}

/** 进度滑块动画的一次性状态快照（值 + 拖动回调） */
private class ProgressSliderAnim(
    val value: Float,
    val onDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit,
)

/**
 * 进度滑块动画控制器：
 * - 播放推进（ProgressTracker 每 250ms 步进一次）→ 显示值用 250ms 平滑插值，进度条连续移动而非跳格；
 * - 拖动 / 点击进度条 → 滑块即时跟手；松手时**一次性** seek（不再拖动中每次回调都 seek），
 *   并停在手指目标位置直到播放器确认（外部进度追上来再平滑续走）—— 避免松手回弹 / 跳变。
 */
@Composable
private fun rememberProgressSliderAnim(
    playFraction: Float,
    duration: Long,
    onSeekFraction: (Float) -> Unit,
): ProgressSliderAnim {
    val scope = rememberCoroutineScope()
    val display = remember { Animatable(playFraction) }
    var dragging by remember { mutableStateOf(false) }
    var dragTarget by remember { mutableStateOf(playFraction) }

    // 外部进度推进 / seek 确认 / 切歌 → 平滑过渡到新目标（用户拖动中不打扰手指）
    LaunchedEffect(playFraction) {
        if (!dragging) display.animateTo(playFraction, tween(250))
    }
    // 无时长（停止 / 切歌瞬间）→ 显示立即归零，避免从旧位置滑回 0 的怪动画
    LaunchedEffect(duration) {
        if (duration <= 0L) {
            dragging = false
            display.snapTo(0f)
        }
    }

    return ProgressSliderAnim(
        value = display.value,
        onDrag = { f ->
            dragging = true
            dragTarget = f
            // 拖动 / 点击时滑块即时跟手（Animatable 并发时新动画取消旧动画，安全）
            scope.launch { display.snapTo(f) }
        },
        onDragEnd = {
            dragging = false
            val target = dragTarget
            // 松手后显示停在目标；播放器确认后外部进度更新会触发上方动画续走
            scope.launch { display.snapTo(target) }
            onSeekFraction(target)
        },
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
 * Tab 栏组件。CSS 属性控制形态：
 * - `display: column` → 竖向 FilterChip 堆叠
 * - `style: pills`    → 横向胶囊 FilterChip 行（原默认样式）
 * - 缺省或 `style: tabs` → 横向 PrimaryTabRow 标签栏（默认样式）
 */
@Composable
private fun SlotContext.TabBar() {
    val state = LocalBrowserState.current
    val css = LocalComponentCss.current
    val variant = unquoteCssString(css["style"] ?: "")
    if (css["display"] == "column") {
        // 竖向堆叠（FilterChip）
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MusicCategory.entries.forEach { category ->
                val (burst, burstModifier) = rememberParticleBurstEffect(color = MaterialTheme.colorScheme.primary)
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = {
                        burst.burst()
                        state.selectedCategory = category
                    },
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
                    modifier = Modifier.fillMaxWidth().then(burstModifier),
                )
            }
        }
    } else if (variant == "pills") {
        // 显式胶囊样式：横向胶囊 FilterChip 行（只保留分类图标，紧凑胶囊 + 整行均匀分布）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MusicCategory.entries.forEach { category ->
                val (burst, burstModifier) = rememberParticleBurstEffect(color = MaterialTheme.colorScheme.primary)
                val icon = when (category) {
                    MusicCategory.ALL -> R.drawable.ic_library_music
                    MusicCategory.ARTIST -> R.drawable.ic_person
                    MusicCategory.ALBUM -> R.drawable.ic_disc
                }
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = {
                        burst.burst()
                        state.selectedCategory = category
                    },
                    label = {
                        Icon(
                            painterResource(icon),
                            contentDescription = category.displayName(),
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.then(burstModifier),
                )
            }
        }
    } else {
        // 默认 / style: tabs：横向 PrimaryTabRow 标签栏（底部指示器）
        PrimaryTabRow(
            selectedTabIndex = state.selectedCategory.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            MusicCategory.entries.forEach { category ->
                val (burst, burstModifier) = rememberParticleBurstEffect(color = MaterialTheme.colorScheme.primary)
                Tab(
                    selected = state.selectedCategory == category,
                    onClick = {
                        burst.burst()
                        state.selectedCategory = category
                    },
                    modifier = burstModifier,
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

/**
 * 曲目统计文本：根据当前分类显示“共 X 首歌曲 / 共 X 位歌手 / 共 X 张专辑”。
 * 独立组件，可在 JSON 布局中单独引用（id: track-count）。
 */
@Composable
private fun SlotContext.TrackCountSummary(modifier: Modifier = Modifier) {
    val state = LocalBrowserState.current
    val summaryText = when (state.selectedCategory) {
        MusicCategory.ALL -> stringResource(R.string.track_count, state.tracks.size)
        MusicCategory.ARTIST -> {
            val groups = state.tracks.groupBy { it.artist.ifBlank { stringResource(R.string.unknown_artist) } }
            stringResource(R.string.artist_count, groups.size)
        }
        MusicCategory.ALBUM -> {
            val groups = state.tracks.groupBy { it.album.ifBlank { stringResource(R.string.unknown_album) } }
            stringResource(R.string.album_count, groups.size)
        }
    }
    Text(
        text = summaryText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotContext.Sort() {
    val state = LocalBrowserState.current
    val sortNames = listOf(
        stringResource(R.string.sort_name),
        stringResource(R.string.sort_duration),
        stringResource(R.string.sort_file_size),
        stringResource(R.string.sort_date_added)
    )
    var expanded by remember { mutableStateOf(false) }
    val (sortBurst, sortBurstModifier) = rememberParticleBurstEffect(color = MaterialTheme.colorScheme.primary)

    // 边框实时变化：菜单展开时 primary 高亮加粗，收起时 outlineVariant 常规；动画平滑过渡
    val borderColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(durationMillis = 150),
        label = "sortBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (expanded) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 150),
        label = "sortBorderWidth",
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackCountSummary(modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box {
            // M3 排序触发器：胶囊按钮 + 动态边框 + 箭头（展开时旋转）
            Surface(
                shape = FilterChipDefaults.shape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(borderWidth, borderColor),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .then(sortBurstModifier)
                        .clickable {
                            sortBurst.burst()
                            expanded = true
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = sortNames.getOrElse(state.sortField) { sortNames[0] },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = stringResource(R.string.sort_label),
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (expanded) 180f else 0f),
                    )
                }
            }

            // M3 标准下拉菜单：锚定触发器，点击外部/菜单项后自动关闭
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                sortNames.forEachIndexed { i, name ->
                    val selected = state.sortField == i
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            sortBurst.burst()
                            state.sortField = i
                            expanded = false
                        },
                        trailingIcon = {
                            if (selected) {
                                Text(
                                    text = "✓",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (state.sortAsc) stringResource(R.string.sort_asc)
                            else stringResource(R.string.sort_desc),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    onClick = {
                        sortBurst.burst()
                        state.sortAsc = !state.sortAsc
                        expanded = false
                    },
                    trailingIcon = {
                        Text(
                            text = if (state.sortAsc) "↑" else "↓",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
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
    // 封面卡片网格：item-layout: grid（list 默认）；item-columns 控制列数（1..6）
    layout = when (css["item-layout"]?.trim()) {
        "grid", "card" -> ItemLayout.GRID
        else -> ItemLayout.LIST
    },
    columns = (css["item-columns"]?.trim()?.toIntOrNull() ?: 2).coerceIn(1, 6),
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
        ParticleModalSheet(
            onDismissRequest = { selectedTrack = null },
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
        ParticleAlertDialog(
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

// ══════════════════════════════════════════════
// 迷你播放栏细分组件（pb-* 系列）
// 与全屏 fp-* 同模式：可单独在 JSON 中引用，也可由 playbar 聚合组装
// ══════════════════════════════════════════════

/** 封面缩略图（私有实现）：显示即按需缓存当前播放封面 */
@Composable
private fun SlotContext.PbCover(size: Dp) {
    val track = playerState.currentTrack
    val context = LocalContext.current
    LaunchedEffect(track?.id) {
        if (track != null) withContext(Dispatchers.IO) { cacheCoverFile(context, track, coverCache) }
    }
    AlbumThumb(albumTrack = track, coverCache = coverCache, size = size)
}

/** 歌曲标题（私有实现）：过长时走马灯滚动显示；切歌时淡入淡出过渡（与全屏 fp-title 一致） */
@Composable
private fun SlotContext.PbTitle(modifier: Modifier = Modifier) {
    val title = playerState.currentTrack?.title
        ?: stringResource(com.winter.muplayer.ui.R.string.not_playing)
    Crossfade(
        targetState = title,
        animationSpec = tween(220),
        label = "pbTitle",
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.Immediately,
                spacing = MarqueeSpacing(24.dp),
                repeatDelayMillis = 1000,
                velocity = 40.dp,
            ),
        )
    }
}

/** 歌手名（私有实现）：无曲目时不显示；切歌时淡入淡出过渡 */
@Composable
private fun SlotContext.PbSubtitle(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val currentTrack = playerState.currentTrack
    Crossfade(
        targetState = currentTrack?.artist,
        animationSpec = tween(220),
        label = "pbSubtitle",
    ) { artist ->
        if (artist != null) {
            Text(
                text = artist,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
            )
        }
    }
}

/** 歌曲信息组合（标题 + 歌手） */
@Composable
private fun SlotContext.PbTrackInfo(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        PbTitle()
        PbSubtitle()
    }
}

/** 迷你播放栏控制按钮行（上一首 / 播放暂停 / 下一首 / 队列） */
@Composable
private fun SlotContext.PbControls(
    expanded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isPlaying = playerState.state == PlayerState.PLAYING
    Row(
        modifier = modifier.then(if (expanded) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = if (expanded) Arrangement.SpaceEvenly else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            icon = painterResource(R.drawable.ic_skip_previous),
            onClick = onPrevious,
            size = 36.dp,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp,
        ) {
            val burst = rememberParticleBurstState()
            ParticleBurstBox(state = burst, modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.onPrimary) {
                IconButton(
                    onClick = {
                        burst.burst()
                        if (isPlaying) onPause() else onPlay()
                    },
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
        }
        ControlButton(
            icon = painterResource(R.drawable.ic_skip_next),
            onClick = onNext,
            size = 36.dp,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        val burstQueue = rememberParticleBurstState()
        ParticleBurstBox(state = burstQueue, color = MaterialTheme.colorScheme.onSurface) {
            IconButton(
                onClick = {
                    burstQueue.burst()
                    onOpenQueue()
                },
            ) {
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

// ── 注册组件包装（JSON 中可用 id 引用） ──

/** pb-cover：迷你播放栏封面缩略图。CSS 支持：size */
@Composable
private fun SlotContext.PbCoverComponent() {
    val css = LocalComponentCss.current
    val size = css["size"]?.let { parseCssDp(it) } ?: 56.dp
    PbCover(size)
}

/** pb-title：迷你播放栏歌曲标题 */
@Composable
private fun SlotContext.PbTitleComponent() {
    PbTitle()
}

/** pb-subtitle：迷你播放栏歌手名 */
@Composable
private fun SlotContext.PbSubtitleComponent() {
    PbSubtitle()
}

/** pb-track-info：标题 + 歌手组合（横向布局的中间弹性列） */
@Composable
private fun SlotContext.PbTrackInfoComponent() {
    PbTrackInfo()
}

/** pb-controls：迷你播放栏控制按钮行 */
@Composable
private fun SlotContext.PbControlsComponent() {
    PbControls(expanded = true)
}

/** 解析 CSS render-style 四态：none（无效果）/ semi-tran（半透明）/ blur（毛玻璃）/ liquid（液态玻璃）。值可能带引号。 */
private fun glassRenderMode(css: Map<String, String>): String {
    val v = unquoteCssString(css["render-style"] ?: "")
    return if (v == "semi-tran" || v == "blur" || v == "liquid") v else "none"
}

/** 液态玻璃视觉参数（CSS #playbar liquid-* / blur-radius；缺失/非法时兜底默认值）。
 * edge/refraction 为物理像素 px（由 dp 配置经 density 换算）；surfaceAlpha 为表面基色不透明度；
 * 其余无单位。 */
private class LiquidGlassCssParams(
    val blurRadiusPx: Float,
    val edgeWidthPx: Float,
    val refractionPx: Float,
    val surfaceAlpha: Float,
    val specular: Float,
    val shininess: Float,
    val rimStrength: Float,
    val chromatic: Float,
)

@Composable
private fun resolveLiquidGlassParams(css: Map<String, String>): LiquidGlassCssParams {
    val density = LocalDensity.current
    return remember(css) {
        fun dpToPx(value: String?): Float? = value?.let { parseCssDp(it) }?.let { with(density) { it.toPx() } }
        val blurRadiusPx = dpToPx(css["blur-radius"]) ?: 0f
        LiquidGlassCssParams(
            blurRadiusPx = blurRadiusPx,
            edgeWidthPx = dpToPx(css["liquid-edge"])
                ?: with(density) { LiquidGlassBackdrop.DEFAULT_EDGE_WIDTH_DP.dp.toPx() },
            refractionPx = dpToPx(css["liquid-refraction"])
                ?: with(density) { LiquidGlassBackdrop.DEFAULT_REFRACTION_DP.dp.toPx() },
            surfaceAlpha = (parseCssNumber(css["liquid-opacity"]) ?: LiquidGlassBackdrop.DEFAULT_SURFACE_ALPHA)
                .coerceIn(0f, 1f),
            specular = parseCssNumber(css["liquid-specular"])
                ?: LiquidGlassBackdrop.DEFAULT_SPECULAR,
            shininess = parseCssNumber(css["liquid-shininess"])
                ?: LiquidGlassBackdrop.DEFAULT_SHININESS,
            rimStrength = parseCssNumber(css["liquid-rim"])
                ?: LiquidGlassBackdrop.DEFAULT_RIM_STRENGTH,
            chromatic = (parseCssNumber(css["liquid-chromatic"]) ?: 0f).coerceIn(0f, 1f),
        )
    }
}

/**
 * 迷你播放栏玻璃表面 —— 基于 AndroidLiquidGlass（`:backdrop` 模块，Kyant backdrop 引擎）：
 * - liquid 模式：按原版 catalog 示例（LiquidBottomTabs / LiquidButton）组装 ——
 *   引用被覆盖内容层（captureLiquidGlassContent 录制的 LayerBackdrop），在自身后方
 *   以正确位置绘制内容层，叠加 vibrancy（增饱和）+ blur + lens（AGSL 折射/色散）
 *   + 常驻轻高光 + 常驻轻阴影 + 高透半透明基色；
 * - blur 模式：毛玻璃 —— blur + 半透明基色 + 轻投影 + 常驻轻高光（无折射 / 无增饱和）；
 * - semi-tran 模式：仅半透明基色 + 轻投影（露出下方清晰内容，不模糊）；
 * - none：不透明悬浮卡片（纯色底 + 与其它模式一致的轻投影，保持悬浮观感）。
 */
@Composable
private fun Modifier.liquidGlassSurface(mode: String, params: LiquidGlassCssParams): Modifier {
    if (mode == "none") {
        // 不透明卡片也悬浮：叠加与 blur/semi-tran 一致的轻投影（12dp 圆角阴影）
        return this.then(
            Modifier.shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
        )
    }

    val glassBackdrop = LocalGlassBackdrop.current
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(20.dp)
    val liquidOn = mode == "liquid"
    // blur / liquid 都录制内容层；blur-radius=0 时仅录制不模糊（lens 折射不依赖模糊）
    val contentOn = mode == "blur" || liquidOn
    val highlightAlpha = params.specular.coerceIn(0f, 1f)
    // shininess → SDF 高光 falloff：默认 48 对应 Kyant 默认 falloff=1（数值越大越锐利）
    val falloff = (params.shininess / LiquidGlassBackdrop.DEFAULT_SHININESS).coerceIn(0.1f, 8f)
    // rim 边缘亮线 alpha（1dp 白边，玻璃边缘标志；默认 0.3，滑块/CSS 可调）
    val rimAlpha = (params.rimStrength * 0.36f).coerceIn(0f, 1f)

    // blur / liquid 模式：引用内容层（列表层录制）；semi-tran：空背景（半透明透出下方）
    val backdrop = if (contentOn) {
        glassBackdrop?.layerBackdrop ?: emptyBackdrop()
    } else {
        emptyBackdrop()
    }

    // 激活状态同步：contentOn 时内容层才录制（captureLiquidGlassContent 依据 active）
    LaunchedEffect(glassBackdrop, contentOn) {
        glassBackdrop?.active = contentOn
    }

    return this.then(
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (contentOn) {
                    if (liquidOn) vibrancy()
                    // 模糊半径 > 0 才模糊；blur-radius=0 时仍保留折射/高光（液态玻璃不依赖模糊）
                    if (params.blurRadiusPx > 0f) blur(params.blurRadiusPx)
                    if (liquidOn) {
                        // 原版示例参数（dp 语义）：LiquidBottomTabs lens(24dp, 24dp)、
                        // LiquidButton lens(12dp, 24dp)；折射作用于 blur 输出（chain 顺序已修复），
                        // depthEffect 示例默认 false，不传；色散可选
                        lens(
                            refractionHeight = params.edgeWidthPx,
                            refractionAmount = params.refractionPx,
                            chromaticAberration = params.chromatic > 0f,
                        )
                    }
                }
            },
            // 常驻轻高光：blur（毛玻璃）与 liquid（液态玻璃）都有，强度由 liquid-specular 控制，
            // 可设 0 关闭（示例高光为按压 pressProgress 驱动，mini 播放栏无按压动画，故保留弱常驻版本）
            highlight = if (contentOn && highlightAlpha > 0f) {
                {
                    Highlight.Default.copy(
                        alpha = highlightAlpha,
                        style = HighlightStyle.Default(falloff = falloff),
                    )
                }
            } else {
                null
            },
            shadow = {
                if (liquidOn) {
                    // 原版 LiquidSlider / LiquidToggle 常驻轻阴影
                    Shadow(
                        radius = 4.dp,
                        color = Color.Black.copy(alpha = 0.05f),
                    )
                } else {
                    // blur / semi-tran：轻投影
                    Shadow(
                        radius = 12.dp,
                        offset = DpOffset(0.dp, 5.dp),
                        color = Color.Black.copy(alpha = 0.10f),
                    )
                }
            },
            innerShadow = if (liquidOn) {
                {
                    // 常驻弱内阴影：玻璃边缘内凹厚度感（原版示例为按压驱动，mini 栏无按压故弱化常驻）
                    InnerShadow(
                        radius = 10.dp,
                        offset = DpOffset(0.dp, 3.dp),
                        color = Color.Black.copy(alpha = 0.12f),
                        alpha = 0.35f,
                    )
                }
            } else {
                null
            },
            onDrawSurface = {
                // 表面基色不透明度：liquid 由 liquid-opacity 参数控制（默认 0.15，玻璃质感不遮内容）；
                // blur / semi-tran 用固定 0.45
                drawRect(surface.copy(alpha = if (liquidOn) params.surfaceAlpha else 0.45f))
                if (liquidOn) {
                    // 顶部反光渐变：单一光源自上而下的柔和亮带，增强玻璃表面质感
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.15f),
                            0.25f to Color.White.copy(alpha = 0.05f),
                            0.6f to Color.Transparent,
                        )
                    )
                    // rim 边缘光：1dp 白色细边框，模拟光打在玻璃边缘
                    if (rimAlpha > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = rimAlpha),
                            style = Stroke(width = 1.dp.toPx()),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                        )
                    }
                }
            },
        )
    )
}

/** pb-backdrop：迷你播放栏容器背景层（卡片样式，整卡可点击打开全屏播放器）。
 *  CSS 支持：render-style（none 默认 / semi-tran 半透明 / blur 毛玻璃 / liquid 液态玻璃） */
@Composable
private fun SlotContext.PbBackdrop() {
    val css = LocalComponentCss.current
    val mode = glassRenderMode(css)
    val params = resolveLiquidGlassParams(css)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlassSurface(mode, params),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        color = if (mode == "none") MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        onClick = onOpenFullPlayer,
    ) {
        // none：卡片底色由 Surface color 直接提供（无多余背景层，避免容器黑边）
    }
}

/** 迷你播放栏聚合容器（默认布局）：Surface 卡片 + 按父 slot 方向组装 pb-* 细分组件。
 *  CSS 支持：render-style（none 默认 / semi-tran 半透明 / blur 毛玻璃 / liquid 液态玻璃） */
@Composable
private fun SlotContext.PlayBar() {
    val css = LocalComponentCss.current
    val mode = glassRenderMode(css)
    val params = resolveLiquidGlassParams(css)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .liquidGlassSurface(mode, params),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        color = if (mode == "none") MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        onClick = onOpenFullPlayer,
    ) {
        // 尺寸由前景内容决定；背景（液态玻璃基色 / 纯色卡片）由 Surface 外层 modifier 绘制
        Box {
            // 横向父 slot（含 overlay 浮层，如 app-center 叠放中的 playbar）→ 横向紧凑布局；
            // 仅纵向父 slot（arrange: column）才切换为竖向堆叠。避免切换渲染样式（线性↔浮层）
            // 时父 slot 方向变化导致按钮排列误变。
            if (isSlotVertical && slotArrange != "overlay") {
                // 竖向父 slot → 垂直堆叠：封面→信息→控制
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PbCover(80.dp)
                    PbTitle(modifier = Modifier.padding(top = 8.dp))
                    PbSubtitle(style = MaterialTheme.typography.bodySmall)
                    PbControls(expanded = true, modifier = Modifier.padding(top = 12.dp))
                }
                return@Box
            }

            // 横向父 slot → 封面 + 信息（弹性中间列） + 控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PbCover(56.dp)
                Spacer(Modifier.width(12.dp))
                PbTrackInfo(modifier = Modifier.weight(1f))
                PbControls(expanded = false)
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
            // 按需缓存当前播放封面（使用才缓存，LRU 上限裁剪）
            withContext(Dispatchers.IO) { cacheCoverFile(context, currentTrack, coverCache) }
            // 全屏模糊背景：直接使用原始无损封面
            val uri = com.winter.muplayer.ui.components.getAlbumArtUri(
                currentTrack, coverCache, preferOriginal = true
            )
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
        // 按需缓存当前播放封面（使用才缓存，LRU 上限裁剪）
        withContext(Dispatchers.IO) { cacheCoverFile(context, track, coverCache) }
        // 全屏主封面：直接使用原始无损封面
        val uri: Any? = com.winter.muplayer.ui.components.getAlbumArtUri(
            track, coverCache, preferOriginal = true
        )
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
                // 无封面占位：边框 + 比周围（surfaceVariant）略深的填充 + 居中图标
                val borderColor = MaterialTheme.colorScheme.outlineVariant
                val innerCorner = albumArtCorner - 8.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(innerCorner))
                        .background(darker(MaterialTheme.colorScheme.surfaceVariant, 0.9f))
                        .border(
                            width = 1.5.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(innerCorner),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(com.winter.muplayer.ui.R.drawable.ic_music_off),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
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
        modifier = Modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            animationMode = MarqueeAnimationMode.Immediately,
            spacing = MarqueeSpacing(24.dp),
            repeatDelayMillis = 1000,
            velocity = 40.dp,
        ),
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
    // 跟随背面模糊封面的亮度：亮封面 → 深色，暗封面 → 白色（高对比）
    val tint = if (adaptiveTint != Color.Unspecified) adaptiveTint else MaterialTheme.colorScheme.onSurface
    val duration = progressData.duration
    val playFraction = if (duration > 0)
        progressData.progress.toFloat() / duration.toFloat()
    else 0f
    // 播放推进平滑 + 拖动/点击跟手、松手一次性 seek（不回弹）
    val anim = rememberProgressSliderAnim(playFraction, duration) { fraction ->
        onSeek((fraction * duration).toLong())
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = anim.value,
            onValueChange = anim.onDrag,
            onValueChangeFinished = anim.onDragEnd,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint,
                inactiveTrackColor = tint.copy(alpha = 0.3f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration((anim.value * duration).toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = tint.copy(alpha = 0.75f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = tint.copy(alpha = 0.75f)
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
        // 竖向父 slot → 全部操控按钮单排均分排列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayModeButton(playMode = playMode, onClick = {
                onPlayModeChange(nextPlayMode(playMode))
            }, tint = tint)
            ControlButton(icon = painterResource(R.drawable.ic_skip_previous), onClick = onPrevious, size = 40.dp, tint = tint)
            PlayPauseButton(
                isPlaying = isPlaying,
                isLoading = playerState.state == PlayerState.LOADING,
                onPlay = onPlay, onPause = onPause,
                containerColor = tint.copy(alpha = 0.2f), iconTint = tint,
            )
            ControlButton(icon = painterResource(R.drawable.ic_skip_next), onClick = onNext, size = 40.dp, tint = tint)
            val burstQueueV = rememberParticleBurstState()
            ParticleBurstBox(state = burstQueueV, color = tint) {
                IconButton(
                    onClick = {
                        burstQueueV.burst()
                        onOpenQueue()
                    },
                ) {
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
                val burstQueueH = rememberParticleBurstState()
                ParticleBurstBox(state = burstQueueH, color = tint) {
                    IconButton(
                        onClick = {
                            burstQueueH.burst()
                            onOpenQueue()
                        },
                    ) {
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
}