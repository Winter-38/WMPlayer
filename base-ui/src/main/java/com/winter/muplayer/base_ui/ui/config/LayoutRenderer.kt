package com.winter.muplayer.base_ui.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 */
@Composable
fun SlotRenderer(
    slots: Map<String, List<ComponentEntry>>,
    context: SlotContext,
    css: CssRuleTable = LocalCssRules.current,
    customComponents: Map<String, Map<String, Any?>> = emptyMap(),
    debug: Boolean = false,
) {
    CompositionLocalProvider(LocalCssRules provides css) {
        Column(modifier = Modifier.fillMaxSize()) {
            var isFirst = true
            var slotIndex = 0
            var componentIndex = 0
            for ((slotName, components) in slots) {
                val slotCss = css.rules[".$slotName"] ?: css.rules[slotName] ?: emptyMap()
                val outerMod = if (isFirst) {
                    isFirst = false; Modifier.statusBarsPadding()
                } else {
                    Modifier
                }

                val rawWeight = slotCss["weight"]?.toFloatOrNull()
                val arrange = slotCss["arrange"]
                val gapDp = slotCss["gap"]?.let { parseDpValue(it) }

                val weight = rawWeight ?: 1f
                // weight: 0 时内部 Row/Column 不应 fillMaxHeight，否则会抢走所有空间
                val fillHeight = rawWeight == null || rawWeight > 0f
                // slot 级 weight 在 ColumnScope 内构建（否则 .weight() 不可调用）
                val slotMod: Modifier = when {
                    rawWeight == null -> Modifier.weight(1f)      // 未设 → 默认平分
                    rawWeight > 0f   -> Modifier.weight(rawWeight) // 显式正值 → 按比例
                    else             -> Modifier                  // weight: 0 → 包裹内容
                }
                val slotBgCss = slotCss["background-color"]?.let { parseCssColor(it) }

                val debugColors = listOf(
                    Color(0xFFFF4444), Color(0xFF44FF44), Color(0xFF4488FF),
                    Color(0xFFFF88FF), Color(0xFFFFDD44), Color(0xFF44FFFF),
                )
                // 仅 debug 时在 slot 级别轮询颜色，生产路径只看 CSS 显式 background-color
                val slotModifier = if (debug) {
                    slotIndex++
                    val dc = debugColors[(slotIndex - 1) % debugColors.size]
                    outerMod
                        .then(slotMod)
                        .fillMaxWidth()
                        .border(3.dp, dc)
                        .background(slotBgCss ?: dc.copy(alpha = 0.06f))
                        .applyPaddingProps(slotCss)
                } else {
                    outerMod
                        .then(slotMod)
                        .fillMaxWidth()
                        .let { if (slotBgCss != null) it.background(slotBgCss) else it }
                        .applyPaddingProps(slotCss)
                }

                Box(modifier = slotModifier) {
                    // 内容容器：Row（水平）或 Column（垂直）
                    val content: @Composable () -> Unit = {
                        if (arrange == "horizontal" || arrange == "row") {
                            Row(
                                modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.fillMaxHeight() else it },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (gapDp != null) Arrangement.spacedBy(gapDp) else Arrangement.Start,
                            ) {
                                // ═══ RowScope 内，weight() 可用 ═══
                                for (entry in components) {
                                    val compCss = css.rules["#${entry.id}"] ?: css.rules[entry.id] ?: emptyMap()
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

                                    val weightMod = if (compWeight != null && compWeight > 0f) {
                                        Modifier.weight(compWeight).then(mod)
                                    } else {
                                        mod
                                    }

                                    CompositionLocalProvider(
                                        LocalSlotContext provides context.copy(slotName = slotName),
                                        LocalComponentExtra provides entry.extra,
                                        LocalComponentCss provides compCss,
                                    ) {
                                        val renderer: @Composable (Modifier) -> Unit = { mod ->
                                            val children = entry.extra["children"]
                                            if (children is Map<*, *>) {
                                                @Suppress("UNCHECKED_CAST")
                                                val childSlots = children as Map<String, List<ComponentEntry>>
                                                Box(modifier = mod) {
                                                    Column(Modifier.fillMaxSize()) {
                                                        ComponentRegistry.render(entry.id, Modifier)
                                                        SlotRenderer(
                                                            slots = childSlots,
                                                            context = context,
                                                            css = css,
                                                            customComponents = customComponents,
                                                            debug = debug,
                                                        )
                                                    }
                                                }
                                            } else {
                                                val def = customComponents[entry.id]
                                                if (def != null && def["icon"] is String) {
                                                    renderCustomIcon(def, mod)
                                                } else {
                                                    if (entry.isCustom) {
                                                        android.util.Log.w("SlotRenderer", "Custom component #${entry.id} not defined in JSON — falling back to built-in")
                                                    }
                                                    ComponentRegistry.render(entry.id, mod)
                                                }
                                            }
                                        }
                                        val alignSelf = compCss["align-self"]
                                        val renderWithAlign: @Composable () -> Unit = {
                                            when (alignSelf) {
                                                "stretch" -> renderer(weightMod.fillMaxHeight())
                                                else -> {
                                                    val align = parseAlignSelfRow(alignSelf)
                                                    if (align != null) {
                                                        Box(modifier = weightMod, contentAlignment = align) {
                                                            renderer(Modifier)
                                                        }
                                                    } else {
                                                        renderer(weightMod)
                                                    }
                                                }
                                            }
                                        }
                                        if (animWrapper != null) {
                                            animWrapper(weightMod) { renderWithAlign() }
                                        } else {
                                            renderWithAlign()
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth().let { if (fillHeight) it.fillMaxHeight() else it },
                                verticalArrangement = if (gapDp != null) Arrangement.spacedBy(gapDp) else Arrangement.Top,
                            ) {
                                // ═══ ColumnScope 内，weight() 可用 ═══
                                for (entry in components) {
                                    val compCss = css.rules["#${entry.id}"] ?: css.rules[entry.id] ?: emptyMap()
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

                                    val weightMod = if (compWeight != null && compWeight > 0f) {
                                        Modifier.weight(compWeight).then(mod)
                                    } else {
                                        mod
                                    }

                                    CompositionLocalProvider(
                                        LocalSlotContext provides context.copy(slotName = slotName),
                                        LocalComponentExtra provides entry.extra,
                                        LocalComponentCss provides compCss,
                                    ) {
                                        val renderer: @Composable (Modifier) -> Unit = { mod ->
                                            val children = entry.extra["children"]
                                            if (children is Map<*, *>) {
                                                @Suppress("UNCHECKED_CAST")
                                                val childSlots = children as Map<String, List<ComponentEntry>>
                                                Box(modifier = mod) {
                                                    Column(Modifier.fillMaxSize()) {
                                                        ComponentRegistry.render(entry.id, Modifier)
                                                        SlotRenderer(
                                                            slots = childSlots,
                                                            context = context,
                                                            css = css,
                                                            customComponents = customComponents,
                                                            debug = debug,
                                                        )
                                                    }
                                                }
                                            } else {
                                                val def = customComponents[entry.id]
                                                if (def != null && def["icon"] is String) {
                                                    renderCustomIcon(def, mod)
                                                } else {
                                                    if (entry.isCustom) {
                                                        android.util.Log.w("SlotRenderer", "Custom component #${entry.id} not defined in JSON — falling back to built-in")
                                                    }
                                                    ComponentRegistry.render(entry.id, mod)
                                                }
                                            }
                                        }
                                        val alignSelf = compCss["align-self"]
                                        val renderWithAlign: @Composable () -> Unit = {
                                            when (alignSelf) {
                                                "stretch" -> renderer(weightMod.fillMaxWidth())
                                                else -> {
                                                    val align = parseAlignSelfColumn(alignSelf)
                                                    if (align != null) {
                                                        Box(modifier = weightMod, contentAlignment = align) {
                                                            renderer(Modifier)
                                                        }
                                                    } else {
                                                        renderer(weightMod)
                                                    }
                                                }
                                            }
                                        }
                                        if (animWrapper != null) {
                                            animWrapper(weightMod) { renderWithAlign() }
                                        } else {
                                            renderWithAlign()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Slot 级 weight 已通过 slotMod 挂入 modifier 链
                    content()

                    // debug 模式叠加层 — slot 边框色 + 左上角标签
                    if (debug) {
                        val dc = debugColors[(slotIndex - 1) % debugColors.size]
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

/** 从 CSS 属性构建组件级别的 Modifier（视觉样式，不含 weight——weight 在 [SlotRenderer] 行内处理） */
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

/** SlotContext 浅拷贝替换 slotName */
private fun SlotContext.copy(slotName: String) = SlotContext(
    slotName = slotName,
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
)
