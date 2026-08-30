package com.winter.muplayer.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 插槽渲染器 —— 每个 slot 读 CSS 决定容器类型和布局。
 * 支持 CSS 属性：weight / arrange / gap
 *
 * 外层容器方向由 [.main] 的 `arrange` 控制：
 *   .main { arrange: column } — slot 垂直堆叠（默认）
 *   .main { arrange: row }    — slot 水平排列
 *
 * 全屏播放器等独立渲染树可传 [outerArrange] 覆盖外层方向，
 * 不再读取 `.main`，与主界面互不影响。
 */
@Composable
fun SlotRenderer(
    slots: Map<String, List<ComponentEntry>>,
    context: SlotContext,
    css: CssRuleTable = LocalCssRules.current,
    customComponents: Map<String, Map<String, Any?>> = emptyMap(),
    debug: Boolean = false,
    /**
     * 显式外层排列方向（arrange 值）。非 null 时替代 `.main` 决定本层及后代递归的
     * 外层方向，用于全屏播放器等独立于主界面的渲染树；null 时保持默认从 `.main` 读取。
     * 容器组件递归渲染 children 时，若容器自身 CSS 有 `arrange` 则优先于本值。
     */
    outerArrange: String? = null,
) {
    CompositionLocalProvider(LocalCssRules provides css) {
        // 进度数据独立收集：仅订阅 LocalProgress 的组件（text bind position/duration、progress-slider）
        // 随进度高频重组，其余组件因参数稳定而跳过，避免整个 UI 每 250ms 全量重组。
        val progress by context.musicPlayerCore.progressState.collectAsState()
        CompositionLocalProvider(LocalProgress provides progress) {
            val layoutCss = css.rules[".main"] ?: emptyMap()
            val layoutArrange = outerArrange ?: layoutCss["arrange"]
            val isLayoutRow = layoutArrange == "row" || layoutArrange == "horizontal"
            val isLayoutOverlay = layoutArrange == "overlay"
            val outerMod = Modifier.fillMaxSize().applyCssProps(layoutCss).statusBarsPadding()

            if (isLayoutOverlay) {
                // ═══ 叠放：子 slot 在 Box 中按声明顺序 z 序叠加（后声明在上） ═══
                // 定位由子 slot CSS `align`（9 宫格）控制；未设 align 的 slot 默认铺满叠放区。
                // 液态玻璃背景（如 mini 播放器浮层）由 backdrop 协调：content 录制（layerBackdrop）
                // + 浮层引用内容层绘制（drawBackdrop，AndroidLiquidGlass 引擎）。
                val backdrop = rememberLiquidGlassBackdrop()
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    Box(modifier = outerMod) {
                        OverlaySlots(
                            slots = slots, context = context, css = css,
                            customComponents = customComponents, debug = debug,
                        )
                    }
                }
            } else if (isLayoutRow) {
                Row(modifier = outerMod) {
                    SlotRendererBody(
                        slots = slots, context = context, css = css,
                        customComponents = customComponents, debug = debug,
                        outerArrange = outerArrange,
                        parentIsColumn = false,
                        crossAxisFill = Modifier::fillMaxHeight,
                        weightFn = { w -> Modifier.weight(w) },
                    )
                }
            } else {
                Column(modifier = outerMod) {
                    SlotRendererBody(
                        slots = slots, context = context, css = css,
                        customComponents = customComponents, debug = debug,
                        outerArrange = outerArrange,
                        parentIsColumn = true,
                        crossAxisFill = Modifier::fillMaxWidth,
                        weightFn = { w -> Modifier.weight(w) },
                    )
                }
            }
        }
    }
}

/**
 * Slot 循环体 —— 在外层 Row 或 Column 的作用域内渲染所有 slot。
 * [crossAxisFill] 控制 slot 在交叉轴方向的填充（Row 外 → fillMaxHeight，Column 外 → fillMaxWidth）。
 * [weightFn] 是当前作用域内 Modifier.weight() 的捕获，用于在提取的函数中正确解析 scope。
 */
@Composable
private fun SlotRendererBody(
    slots: Map<String, List<ComponentEntry>>,
    context: SlotContext,
    css: CssRuleTable,
    customComponents: Map<String, Map<String, Any?>>,
    debug: Boolean,
    outerArrange: String?,
    /** 父容器是否为 Column（垂直排列）：决定 slot 主轴方向，内部容器据此避免在主轴 fillMaxSize 撑满 */
    parentIsColumn: Boolean,
    crossAxisFill: Modifier.() -> Modifier,
    weightFn: (Float) -> Modifier,
) {
    var slotIndex = 0
    var componentIndex = 0
    for ((slotName, components) in slots) {
        val slotCss = css.rules[".$slotName"] ?: css.rules[slotName] ?: emptyMap()

        val rawWeight = slotCss["weight"]?.toFloatOrNull()
        val arrange = slotCss["arrange"]
        val gapDp = slotCss["gap"]?.let { parseDpValue(it) }

        val weight = rawWeight ?: 1f
        // weight: 0 时内部 Row/Column 不应 fillMaxWidth/fillMaxHeight，否则会抢走所有空间
        val hasWeight = rawWeight == null || rawWeight > 0f
        // 内部内容容器（Row/Column）的填充策略：
        // - 交叉轴方向（垂直于父容器方向）始终填满（父容器交叉轴尺寸明确）；
        // - 主轴方向（父容器方向）由 slot 级 weight 决定：weight: 0 → wrap 内容，
        //   有 weight → 填满（weight 已分配明确尺寸）。
        // 关键修复：父容器为 Column 且内部为 column、weight:0 的 slot（如插件页 header）
        // 若无条件 fillMaxHeight，会在 wrapContentHeight 约束下把内部 Column 撑到
        // 整个剩余高度，把其余 slot 全部挤成 0 高度（色块不可见的根因）。
        val fillHeight = when {
            arrange == "horizontal" || arrange == "row" -> hasWeight          // 内部 Row：高度按 weight
            parentIsColumn -> hasWeight                                         // 内部 Column + 父 Column：高度（主轴）按 weight
            else -> true                                                        // 内部 Column + 父 Row：高度（交叉轴）填满
        }
        val fillWidth = when {
            arrange == "horizontal" || arrange == "row" -> true                // 内部 Row：宽度填满
            parentIsColumn -> true                                               // 内部 Column + 父 Column：宽度（交叉轴）填满
            else -> hasWeight                                                    // 内部 Column + 父 Row：宽度（主轴）按 weight
        }
        // slot 级 weight 由外层 Row/Column scope 捕获的 weightFn 提供
        val slotMod: Modifier = when {
            rawWeight == null -> weightFn(1f)      // 未设 → 默认平分
            rawWeight > 0f   -> weightFn(rawWeight) // 显式正值 → 按比例
            // weight: 0 → 包裹内容。必须 wrapContentHeight：否则内部容器组件的
            // fillMaxSize 背景/前景层会吃满外层固定高度约束（如 .main 全屏），
            // 把迷你播放栏（app-bottom）这类 weight:0 的 slot 撑成整个屏幕。
            else             -> Modifier.wrapContentHeight()
        }
        val slotBgCss = slotCss["background-color"]?.let { parseCssColor(it) }

        val debugColors = listOf(
            Color(0xFFFF4444), Color(0xFF44FF44), Color(0xFF4488FF),
            Color(0xFFFF88FF), Color(0xFFFFDD44), Color(0xFF44FFFF),
        )
        // 仅 debug 时在 slot 级别轮询颜色，生产路径只看 CSS 显式 background-color
        // crossAxisFill 填充交叉轴：Column 外 → fillMaxWidth，Row 外 → fillMaxHeight
        val slotModifier = if (debug) {
            slotIndex++
            val dc = debugColors[(slotIndex - 1) % debugColors.size]
            Modifier
                .then(slotMod)
                .crossAxisFill()
                .border(3.dp, dc)
                .background(slotBgCss ?: dc.copy(alpha = 0.06f))
                .applyPaddingProps(slotCss)
        } else {
            Modifier
                .then(slotMod)
                .crossAxisFill()
                .let { if (slotBgCss != null) it.background(slotBgCss) else it }
                .applyPaddingProps(slotCss)
        }

        Box(modifier = slotModifier) {
            // 内容容器：Row（水平）或 Column（垂直）
            val content: @Composable () -> Unit = {
                if (arrange == "horizontal" || arrange == "row") {
                    Row(
                        modifier = Modifier.let { if (fillWidth) it.fillMaxWidth() else it }
                                           .let { if (fillHeight) it.fillMaxHeight() else it },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = run {
                            val jc = slotCss["justify-content"]
                            val align = parseJustifyAlignment(jc)
                            if (align != null && gapDp != null) {
                                Arrangement.spacedBy(gapDp, align)
                            } else {
                                parseJustifyContent(jc) ?: if (gapDp != null) Arrangement.spacedBy(gapDp) else Arrangement.Start
                            }
                        },
                    ) {
                        // ═══ RowScope 内，weight() 可用 ═══
                        for (entry in components) {
                            val compCss = resolveComponentCss(entry, css)
                            val mod = compCssModifier(compCss).let { m ->
                                if (debug) {
                                    componentIndex++
                                    val dc = debugColors[(componentIndex - 1) % debugColors.size]
                                    m.drawWithContent {
                                        drawContent()
                                        drawRect(color = dc.copy(alpha = 0.12f), size = size)
                                    }
                                } else m
                            }
                            val animWrapper = parseAnimationWrapper(compCss)
                            val compWeight = compCss["weight"]?.toFloatOrNull()
                            // 容器组件（带 children 的背景层，如 fp-backdrop）：未显式设 weight 时默认铺满父容器
                            val isContainer = entry.extra["children"] is Map<*, *>

                            val weightMod = when {
                                compWeight != null && compWeight > 0f -> Modifier.weight(compWeight).then(mod)
                                isContainer -> Modifier.weight(1f).then(mod)
                                else -> mod
                            }

                            CompositionLocalProvider(
                                LocalSlotContext provides context.copy(slotName = slotName, slotArrange = arrange),
                                LocalComponentExtra provides entry.extra,
                                LocalComponentCss provides compCss,
                            ) {
                                val renderer: @Composable (Modifier) -> Unit = { mod ->
                                    if (entry.extra["children"] is Map<*, *>) {
                                        @Suppress("UNCHECKED_CAST")
                                        RenderChildrenContainer(
                                            entry = entry,
                                            mod = mod,
                                            childSlots = entry.extra["children"] as Map<String, List<ComponentEntry>>,
                                            context = context,
                                            css = css,
                                            customComponents = customComponents,
                                            debug = debug,
                                            compCss = compCss,
                                            parentArrange = arrange,
                                        )
                                    } else {
                                        val def = customComponents[entry.id]
                                        if (def != null && def["icon"] is String) {
                                            renderCustomIcon(def, mod)
                                        } else if (entry.isCustom && !ComponentRegistry.isRegistered(entry.id)) {
                                            android.util.Log.w("SlotRenderer", "Custom component #${entry.id} not defined in JSON — skipping (remove # for native or define it)")
                                        } else {
                                            ComponentRegistry.render(entry.id, mod)
                                        }
                                    }
                                }
                                val alignSelf = compCss["align-self"]
                                val rowAlign = parseAlignSelfRow(alignSelf)
                                val finalMod = if (rowAlign != null) {
                                    weightMod.align(rowAlign)
                                } else {
                                    weightMod  // stretch 或未设 → 默认填满交叉轴
                                }
                                if (animWrapper != null) {
                                    animWrapper(finalMod) { renderer(Modifier) }
                                } else {
                                    renderer(finalMod)
                                }
                            }
                        }
                    }
                } else if (arrange == "overlay") {
                    // ═══ 叠放：组件在 Box 中按声明顺序 z 序叠加（后声明在上） ═══
                    // 定位由组件 CSS `align`（9 宫格）控制；容器组件默认铺满叠放区。
                    // 液态玻璃 backdrop 在内容容器层创建，content 与浮层（playbar）共享。
                    val backdrop = rememberLiquidGlassBackdrop()
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        Box(
                            modifier = Modifier.let { if (fillWidth) it.fillMaxWidth() else it }
                                               .let { if (fillHeight) it.fillMaxHeight() else it }
                        ) {
                            OverlayComponents(
                                components = components,
                                context = context, css = css,
                                customComponents = customComponents, debug = debug,
                                slotName = slotName,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.let { if (fillWidth) it.fillMaxWidth() else it }
                                           .let { if (fillHeight) it.fillMaxHeight() else it },
                        verticalArrangement = run {
                            val jc = slotCss["justify-content"]
                            val align = parseJustifyAlignmentVertical(jc)
                            if (align != null && gapDp != null) {
                                Arrangement.spacedBy(gapDp, align)
                            } else {
                                parseJustifyContentVertical(jc) ?: if (gapDp != null) Arrangement.spacedBy(gapDp) else Arrangement.Top
                            }
                        },
                    ) {
                        // ═══ ColumnScope 内，weight() 可用 ═══
                        for (entry in components) {
                            val compCss = resolveComponentCss(entry, css)
                            val mod = compCssModifier(compCss).let { m ->
                                if (debug) {
                                    componentIndex++
                                    val dc = debugColors[(componentIndex - 1) % debugColors.size]
                                    m.drawWithContent {
                                        drawContent()
                                        drawRect(color = dc.copy(alpha = 0.12f), size = size)
                                    }
                                } else m
                            }
                            val animWrapper = parseAnimationWrapper(compCss)
                            val compWeight = compCss["weight"]?.toFloatOrNull()
                            // 容器组件（带 children 的背景层，如 fp-backdrop）：未显式设 weight 时默认铺满父容器
                            val isContainer = entry.extra["children"] is Map<*, *>

                            val weightMod = when {
                                compWeight != null && compWeight > 0f -> Modifier.weight(compWeight).then(mod)
                                isContainer -> Modifier.weight(1f).then(mod)
                                else -> mod
                            }

                            CompositionLocalProvider(
                                LocalSlotContext provides context.copy(slotName = slotName, slotArrange = arrange),
                                LocalComponentExtra provides entry.extra,
                                LocalComponentCss provides compCss,
                            ) {
                                val renderer: @Composable (Modifier) -> Unit = { mod ->
                                    if (entry.extra["children"] is Map<*, *>) {
                                        @Suppress("UNCHECKED_CAST")
                                        RenderChildrenContainer(
                                            entry = entry,
                                            mod = mod,
                                            childSlots = entry.extra["children"] as Map<String, List<ComponentEntry>>,
                                            context = context,
                                            css = css,
                                            customComponents = customComponents,
                                            debug = debug,
                                            compCss = compCss,
                                            parentArrange = arrange,
                                        )
                                    } else {
                                        val def = customComponents[entry.id]
                                        if (def != null && def["icon"] is String) {
                                            renderCustomIcon(def, mod)
                                        } else if (entry.isCustom && !ComponentRegistry.isRegistered(entry.id)) {
                                            android.util.Log.w("SlotRenderer", "Custom component #${entry.id} not defined in JSON — skipping (remove # for native or define it)")
                                        } else {
                                            ComponentRegistry.render(entry.id, mod)
                                        }
                                    }
                                }
                                val alignSelf = compCss["align-self"]
                                val columnAlign = parseAlignSelfColumn(alignSelf)
                                val finalMod = if (columnAlign != null) {
                                    weightMod.align(columnAlign)
                                } else {
                                    weightMod  // stretch 或未设 → 默认填满交叉轴
                                }
                                if (animWrapper != null) {
                                    animWrapper(finalMod) { renderer(Modifier) }
                                } else {
                                    renderer(finalMod)
                                }
                            }
                        }
                    }
                }
            }

            // Slot 级 weight 已通过 slotMod 挂入 modifier 链
            content()

            // debug 模式叠加层 — slot 边框色 + 左上角标签
            // 标签放在 matchParentSize 层：不参与父 Box 尺寸测量，避免撑大 wrap 尺寸的 slot（如 weight: 0）
            if (debug) {
                val dc = debugColors[(slotIndex - 1) % debugColors.size]
                Box(Modifier.matchParentSize()) {
                    Text(
                        text = "$slotName  w=$weight",
                        color = dc,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 渲染自定义图标组件 —— 从定义字典读取 `icon` 名，配合 CSS 控制颜色/尺寸。
 * 需要在 [LocalComponentCss] 的 CompositionLocal 作用域内调用。
 */
@Composable
private fun renderCustomIcon(
    def: Map<String, Any?>,
    modifier: Modifier,
) {
    val iconName = def["icon"] as? String ?: return
    val css = LocalComponentCss.current
    val tintColor = css["color"]?.let { parseCssColor(it) }
        ?: MaterialTheme.colorScheme.onSurface
    val iconSize = css["size"]?.let { parseCssDp(it) } ?: 24.dp

    val context = LocalContext.current
    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
    if (resId == 0) return

    Icon(
        painter = painterResource(resId),
        contentDescription = iconName,
        tint = tintColor,
        modifier = modifier.size(iconSize),
    )
}

/** 从 CSS 属性构建组件级别的 Modifier（视觉样式，不含 weight——weight 在 [SlotRendererBody] 行内处理） */
private fun compCssModifier(props: Map<String, String>): Modifier {
    var m: Modifier = Modifier

    if (props["fillMaxWidth"] == "true") m = m.fillMaxWidth()

    // 视觉 CSS 属性（background / size / opacity / scale / rotate 等）
    m = m.applyCssProps(props)
    return m
}

/** 解析 CSS 长度值（px / dp → Dp） */
private fun parseDpValue(value: String): androidx.compose.ui.unit.Dp {
    val trimmed = value.trim()
    val num = when {
        trimmed.endsWith("px") -> trimmed.removeSuffix("px").trim().toFloatOrNull()
        trimmed.endsWith("dp") -> trimmed.removeSuffix("dp").trim().toFloatOrNull()
        else -> trimmed.toFloatOrNull()
    }
    return (num ?: 0f).dp
}

/** SlotContext 浅拷贝替换 slotName 及 slotArrange */
private fun SlotContext.copy(slotName: String, slotArrange: String? = this.slotArrange) = SlotContext(
    slotName = slotName,
    slotArrange = slotArrange,
    onOpenSearch = onOpenSearch,
    onOpenSettings = onOpenSettings,
    localMusicList = localMusicList,
    isLoadingLocal = isLoadingLocal,
    coverCache = coverCache,
    musicPlayerCore = musicPlayerCore,
    onPlayTrackSmart = onPlayTrackSmart,
    playerState = playerState,
    onPlay = onPlay,
    onPause = onPause,
    onNext = onNext,
    onPrevious = onPrevious,
    onOpenFullPlayer = onOpenFullPlayer,
    onOpenQueue = onOpenQueue,
    onSeek = onSeek,
    onPlayModeChange = onPlayModeChange,
    playMode = playMode,
    adaptiveTint = adaptiveTint,
    blurBackground = blurBackground,
)

/**
 * 容器组件统一渲染 —— Row / Column / 叠放分支共用的容器逻辑。
 *
 * - 普通容器（fp-backdrop / pb-backdrop / 命名子 slot）：自身渲染为背景层 + children 前景层；
 * - `backdrop-blur`（毛玻璃镜像容器）：children **双渲染** —— 背景为 children 的模糊镜像
 *   （实时模糊被覆盖的下方内容），前景为 children 正常渲染；模糊半径取 CSS `blur-radius`，
 *   为 0 时退化为普通单渲染（零额外开销，适合默认配置）。
 */
@Composable
private fun RenderChildrenContainer(
    entry: ComponentEntry,
    mod: Modifier,
    childSlots: Map<String, List<ComponentEntry>>,
    context: SlotContext,
    css: CssRuleTable,
    customComponents: Map<String, Map<String, Any?>>,
    debug: Boolean,
    compCss: Map<String, String>,
    parentArrange: String?,
) {
    if (entry.id == "backdrop-blur") {
        // 毛玻璃镜像层：单渲染 children + 模糊 + 裁剪到容器自身区域。
        // 作为 overlay 中 playbar 下方的独立层（CSS 用 align/height 限定为 playbar 区域），
        // playbar 半透明表面露出该模糊镜像 → 毛玻璃。
        // blur-radius: 0 时无模糊（清晰镜像，即半透明效果，露出下方列表）。
        // 滚动状态由组件内共享 LazyListState 与前景列表同步。children 按自身 arrange 布局。
        val radius = compCss["blur-radius"]?.let { parseDpValue(it) } ?: 0.dp
        val childArrange = compCss["arrange"]
        Box(
            modifier = mod
                .then(if (radius > 0.dp) Modifier.blur(radius) else Modifier)
                .clipToBounds()
        ) {
            SlotRenderer(
                slots = childSlots, context = context, css = css,
                customComponents = customComponents, debug = debug,
                outerArrange = childArrange,
            )
        }
        return
    }
    Box(modifier = mod) {
        // 背景层：容器组件自身 fillMaxSize 铺满（如 fp-backdrop 的模糊封面背景）
        ComponentRegistry.render(entry.id, Modifier.fillMaxSize())
        // 前景层：children 子 slots fillMaxSize 叠在背景之上
        Box(Modifier.fillMaxSize()) {
            SlotRenderer(
                slots = childSlots, context = context, css = css,
                customComponents = customComponents, debug = debug,
                outerArrange = compCss["arrange"] ?: parentArrange,
            )
        }
    }
}

/**
 * 叠放渲染 —— `arrange: overlay` 的顶层实现：子 slot 在 Box 中按声明顺序 z 序叠加
 * （后声明在上），定位由子 slot CSS `align`（9 宫格）控制；未设 align 的 slot 默认铺满叠放区。
 *
 * 典型用法（迷你播放栏浮于列表之上，背景毛玻璃）：
 * ```json
 * "app-center": [
 *   { "name": "content", "children": [{ "backdrop-blur": ["tab-bar", "sort", "playlist"] }] },
 *   "playbar"
 * ]
 * ```
 * ```css
 * .app-center { arrange: overlay; }
 * #playbar    { align: bottom-center; }
 * #backdrop-blur { blur-radius: 24px; }   /* 0 则关闭毛玻璃双渲染 */
 * ```
 */
@Composable
private fun OverlaySlots(
    slots: Map<String, List<ComponentEntry>>,
    context: SlotContext,
    css: CssRuleTable,
    customComponents: Map<String, Map<String, Any?>>,
    debug: Boolean,
) {
    // 外层叠放 Box：子 slot 在其内按 z 序叠加（align 定位只能在 BoxScope 内使用）
    Box(Modifier.fillMaxSize()) {
        for ((slotName, components) in slots) {
        val slotCss = css.rules[".$slotName"] ?: css.rules[slotName] ?: emptyMap()
        val slotAlign = slotCss["align"]?.let { parseBoxAlign(it) }
        val slotBgCss = slotCss["background-color"]?.let { parseCssColor(it) }
        val slotMod: Modifier = if (slotAlign != null) {
            // 显式对齐 → 包裹内容并按 9 宫格定位
            Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .align(slotAlign)
        } else {
            Modifier.fillMaxSize()
        }
        Box(
            modifier = slotMod
                .let { if (slotBgCss != null) it.background(slotBgCss) else it }
                .applyPaddingProps(slotCss)
        ) {
            OverlayComponents(
                components = components,
                context = context, css = css,
                customComponents = customComponents, debug = debug,
                slotName = slotName,
            )
        }
    }
    }
}

/** 叠放层内的组件渲染（Box scope）：按声明顺序 z 序叠加；容器组件铺满，其余按 CSS align 定位。 */
@Composable
private fun BoxScope.OverlayComponents(
    components: List<ComponentEntry>,
    context: SlotContext,
    css: CssRuleTable,
    customComponents: Map<String, Map<String, Any?>>,
    debug: Boolean,
    slotName: String,
) {
    // 液态玻璃 backdrop：由内容容器（overlay 分支）提供，本层 content 应用 capture、playbar 绘制
    val backdrop = LocalGlassBackdrop.current

    for (entry in components) {
        val compCss = resolveComponentCss(entry, css)
        val mod = compCssModifier(compCss)
        val isContainer = entry.extra["children"] is Map<*, *>
        // 容器（子 slot 字典 / fp-backdrop 等）默认铺满叠放区；内容层（匿名容器，即液态玻璃内容源）
        // 额外应用 capture modifier（列表单次录制，供浮层 drawBackdrop 引用）；
        // 普通组件按自身尺寸 + align 定位
        val baseMod = when {
            entry.id == "backdrop-blur" -> mod
            isContainer -> {
                val fill = Modifier.fillMaxSize()
                if (entry.id == LayoutParser.ANONYMOUS_CONTAINER && backdrop != null) {
                    fill.captureLiquidGlassContent(backdrop)
                } else {
                    fill
                }
            }
            else -> mod
        }
        val alignMod = compCss["align"]?.let { Modifier.align(parseBoxAlign(it)) } ?: Modifier

        CompositionLocalProvider(
            LocalSlotContext provides context.copy(slotName = slotName, slotArrange = "overlay"),
            LocalComponentExtra provides entry.extra,
            LocalComponentCss provides compCss,
        ) {
            val renderer: @Composable (Modifier) -> Unit = { renderMod ->
                val children = entry.extra["children"]
                if (children is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    RenderChildrenContainer(
                        entry = entry,
                        mod = renderMod,
                        childSlots = children as Map<String, List<ComponentEntry>>,
                        context = context, css = css,
                        customComponents = customComponents, debug = debug,
                        compCss = compCss,
                        // overlay 中的内容层（如 content）内部按默认线性（.main column）排列，
                        // 不继承 overlay —— 否则 tab/sort/playlist 会被当作叠放层重叠。
                        // backdrop-blur 镜像层自身特判（compCss arrange），不受此值影响。
                        parentArrange = null,
                    )
                } else {
                    val def = customComponents[entry.id]
                    if (def != null && def["icon"] is String) {
                        renderCustomIcon(def, renderMod)
                    } else if (entry.isCustom && !ComponentRegistry.isRegistered(entry.id)) {
                        android.util.Log.w("SlotRenderer", "Custom component #${entry.id} not defined in JSON — skipping (remove # for native or define it)")
                    } else {
                        ComponentRegistry.render(entry.id, renderMod)
                    }
                }
            }
            val animWrapper = parseAnimationWrapper(compCss)
            val finalMod = baseMod.then(alignMod)
            if (animWrapper != null) {
                animWrapper(finalMod) { renderer(Modifier) }
            } else {
                renderer(finalMod)
            }
        }
    }
}

/** 解析叠放定位 CSS `align`（9 宫格）；无效值默认居中。 */
private fun parseBoxAlign(value: String): Alignment {
    return when (value.trim().lowercase()) {
        "top-start", "start-top" -> Alignment.TopStart
        "top-center", "top" -> Alignment.TopCenter
        "top-end", "end-top" -> Alignment.TopEnd
        "center-start", "start" -> Alignment.CenterStart
        "center", "center-center" -> Alignment.Center
        "center-end", "end" -> Alignment.CenterEnd
        "bottom-start", "start-bottom" -> Alignment.BottomStart
        "bottom-center", "bottom" -> Alignment.BottomCenter
        "bottom-end", "end-bottom" -> Alignment.BottomEnd
        else -> Alignment.Center
    }
}
