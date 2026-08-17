package com.winter.muplayer.config

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.core.ProgressTracker
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track

/**
 * 组件上下文 —— 所有注册组件通过 CompositionLocal 获取。
 * 包含渲染可能需要的所有 state 和回调。
 */
class SlotContext(
    /** 当前渲染的插槽名 */
    val slotName: String,
    // ── 搜索（改为全屏入口）──
    val onOpenSearch: () -> Unit,
    val onOpenSettings: () -> Unit,
    // ── 歌单 ──
    val localMusicList: List<Track>,
    val isLoadingLocal: Boolean,
    val coverCache: Map<Long, String>,
    val musicPlayerCore: MusicPlayerCore,
    val onPlayTrackSmart: (Track, List<Track>) -> Unit,
    // ── 播放控制 ──
    val playerState: PlayerStateData,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onOpenFullPlayer: () -> Unit,
    val onOpenQueue: () -> Unit,
    // ── 全屏播放器专属 ──
    val onSeek: (Long) -> Unit = { _ -> },
    val onPlayModeChange: (PlayMode) -> Unit = {},
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val adaptiveTint: Color = Color.Unspecified,
    /** 全屏播放器是否启用封面模糊背景（来自设置，fp-backdrop 组件据此渲染） */
    val blurBackground: Boolean = false,
    /** 父 slot 的 CSS arrange 值（"row"/"column"/null），组件可据此切换竖向/横向布局 */
    val slotArrange: String? = null,
)

/** 父 slot 为横向排列（arrange: row/horizontal） */
val SlotContext.isSlotHorizontal: Boolean
    get() = slotArrange == "row" || slotArrange == "horizontal"

/** 父 slot 为竖向排列（arrange: column/vertical 或未设置） */
val SlotContext.isSlotVertical: Boolean
    get() = !isSlotHorizontal

/** CompositionLocal 承载当前 slot 上下文 */
val LocalSlotContext = compositionLocalOf<SlotContext> {
    error("SlotContext not provided — wrap SlotRenderer in CompositionLocalProvider")
}

/** CompositionLocal 承载当前组件的 extra 属性（来自 JSON layout） */
val LocalComponentExtra = compositionLocalOf<Map<String, Any?>> { emptyMap() }

/** CompositionLocal 承载当前播放进度 —— 独立于 playerState，仅进度条订阅，避免高频进度更新拖垮全 UI 重组 */
val LocalProgress = compositionLocalOf { ProgressTracker.ProgressData() }

/**
 * 组件注册表 —— 全局单例。
 * 任意模块调用 register(id) 注册自己的 Composable 渲染器，
 * JSON 布局文件中以 #id 引用即可自动渲染。
 */
object ComponentRegistry {

    private val registry = mutableMapOf<String, @Composable SlotContext.() -> Unit>()

    /** 注册一个组件渲染器 */
    fun register(id: String, renderer: @Composable SlotContext.() -> Unit) {
        registry[id] = renderer
    }

    /** 根据 ID 渲染组件，modifier 包裹组件（来自 CSS 样式）；未注册时静默跳过 */
    @Composable
    fun render(id: String, modifier: Modifier = Modifier) {
        val ctx = LocalSlotContext.current
        val css = LocalComponentCss.current
        val contentAlign = css["content-align"]?.let { parseContentAlign(it) } ?: Alignment.Center
        registry[id]?.let {
            Box(modifier = modifier, contentAlignment = contentAlign) { ctx.it() }
        }
    }

    /** 批量注册（内置组件用） */
    fun registerAll(vararg entries: Pair<String, @Composable SlotContext.() -> Unit>) {
        entries.forEach { (id, renderer) -> register(id, renderer) }
    }
}