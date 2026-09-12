package com.winter.muplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winter.muplayer.config.ComponentRegistry
import com.winter.muplayer.config.LocalComponentCss
import com.winter.muplayer.config.LocalComponentExtra
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.parseCssBackgroundBrush
import com.winter.muplayer.config.parseCssColor
import com.winter.muplayer.config.parseCssDp
import com.winter.muplayer.config.parseCssNumber
import com.winter.muplayer.config.parseCssPxFloat

/**
 * 纯美化组件集合 —— 只影响外观、不参与业务逻辑的通用布局组件。
 *
 * 与 `BuiltInComponents` 中的业务组件（playlist / playbar / 播放控制等）分开放置：
 * 这里的组件只依赖 CSS 属性与 JSON 内联参数，可以放进任意 slot 组合出界面。
 *
 * ## 组件与特色属性速查
 *
 * ### divider 分割线
 * | 属性 | 说明 |
 * |------|------|
 * | `thickness` | 线宽（默认 `1px`） |
 * | `color` | 线色（默认主题 `outlineVariant`） |
 * | `orientation` | `horizontal`（默认）/ `vertical` |
 * | `dashed` | `true` 时虚线（自绘 Canvas） |
 * | `dash-gap` | 虚线段与间隔长度（px，默认 4） |
 * | `fade-edges` | `true` 时两端渐隐 |
 *
 * ### image 图片
 * | 属性 | 说明 |
 * |------|------|
 * | `src`（CSS 或 JSON） | drawable 资源名 / 文件路径 / `content://` / `http(s)://` |
 * | `fit` | `cover` / `contain`（默认）/ `fill` / `fitWidth` / `fitHeight` |
 * | `tint` | 着色 |
 * | `alpha` | 0..1 |
 *
 * ### card 卡片（纯背景，非容器）
 * | 属性 | 说明 |
 * |------|------|
 * | `radius` | 圆角（回退 `border-radius`，默认 12px） |
 * | `fill-color` | 填充色（回退 `background-color`） |
 * | `stroke-color` / `stroke-width` | 描边 |
 * | `elevation` | 阴影高度（默认 0） |
 *
 * ### badge 徽章
 * | 属性 | 说明 |
 * |------|------|
 * | `content`（CSS 或 JSON） | 文本 |
 * | `fill-color` / `text-color` / `text-size` | 外观 |
 * | `pill` | `true` 时全圆角 |
 * | `stroke-color` / `stroke-width` | 描边 |
 * | `padding-x` / `padding-y` | 内边距 |
 *
 * ### dot 圆点
 * | 属性 | 说明 |
 * |------|------|
 * | `size` | 直径（默认 8px） |
 * | `color` | 颜色 |
 * | `hollow` | `true` 时只描边 |
 * | `stroke-width` | 描边宽度（默认 2px） |
 *
 * ### progress 静态进度条
 * | 属性 | 说明 |
 * |------|------|
 * | `value`（CSS 或 JSON） | 0..1，或 0..100（>1 视为百分比） |
 * | `track-color` / `fill-color` | 轨道与填充色 |
 * | `height` | 条粗细（默认 4px） |
 * | `radius` | 圆角（默认 2px） |
 * | `animate` | `true` 时数值变化带动画过渡 |
 *
 * ### blur-layer 模糊层
 * | 属性 | 说明 |
 * |------|------|
 * | `blur-radius` | 模糊半径（默认 0 = 不模糊） |
 * | `tint` / `tint-alpha` | 叠加色调与不透明度 |
 * | `background` | 可先用 `linear-gradient(...)` 铺底再模糊（形成光斑） |
 */
fun registerBeautifyComponents() {
    ComponentRegistry.registerAll(
        "divider" to { DividerComponent() },
        "image" to { ImageComponent() },
        "card" to { CardComponent() },
        "badge" to { BadgeComponent() },
        "dot" to { DotComponent() },
        "progress" to { ProgressComponent() },
        "blur-layer" to { BlurLayerComponent() },
    )
}

/** 去除 CSS 字符串值首尾引号（支持单/双引号）。 */
private fun unquoteCss(value: String): String {
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

private fun cssFlag(value: String?): Boolean = value?.trim()?.equals("true", ignoreCase = true) == true

// ==================== divider ====================

@Composable
private fun SlotContext.DividerComponent() {
    val css = LocalComponentCss.current
    val thickness = remember(css) { css["thickness"]?.let { parseCssDp(it) } ?: 1.dp }
    val color = remember(css) { css["color"]?.let { parseCssColor(it) } }
    val vertical = remember(css) { css["orientation"]?.trim()?.equals("vertical", true) == true }
    val dashed = remember(css) { cssFlag(css["dashed"]) }
    val dashGap = remember(css) { css["dash-gap"]?.let { parseCssPxFloat(it) } ?: 4f }
    val fadeEdges = remember(css) { cssFlag(css["fade-edges"]) }

    val lineColor = color ?: MaterialTheme.colorScheme.outlineVariant
    val sizeMod = if (vertical) Modifier.fillMaxHeight().width(thickness)
    else Modifier.fillMaxWidth().height(thickness)

    if (dashed) {
        // 虚线必须自绘：单个 DashPathEffect 沿轴线铺开，线宽取交叉轴尺寸
        Canvas(modifier = sizeMod) {
            val gap = dashGap.coerceAtLeast(1f)
            val effect = PathEffect.dashPathEffect(floatArrayOf(gap, gap), 0f)
            if (vertical) {
                val x = size.width / 2f
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = size.width,
                    cap = StrokeCap.Round,
                    pathEffect = effect,
                )
            } else {
                val y = size.height / 2f
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                    pathEffect = effect,
                )
            }
        }
    } else {
        val brush: Brush = if (fadeEdges) {
            if (vertical) Brush.verticalGradient(listOf(Color.Transparent, lineColor, Color.Transparent))
            else Brush.horizontalGradient(listOf(Color.Transparent, lineColor, Color.Transparent))
        } else {
            SolidColor(lineColor)
        }
        Box(sizeMod.background(brush))
    }
}

// ==================== image ====================

@Composable
private fun SlotContext.ImageComponent() {
    val css = LocalComponentCss.current
    val extra = LocalComponentExtra.current
    val context = LocalContext.current

    // JSON 内联 src 优先于 CSS src
    val src = (extra["src"] as? String)?.takeIf { it.isNotBlank() }
        ?: css["src"]?.let { unquoteCss(it) }?.takeIf { it.isNotBlank() }
        ?: return

    val scale = remember(css) { parseImageContentScale(css["fit"]) }
    val tint = remember(css) { css["tint"]?.let { parseCssColor(it) } }
    val alphaValue = remember(css) {
        (css["alpha"]?.let { parseCssNumber(it) } ?: 1f).coerceIn(0f, 1f)
    }
    val resId = remember(src) {
        context.resources.getIdentifier(src, "drawable", context.packageName)
    }
    val colorFilter = tint?.let { ColorFilter.tint(it) }

    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = Modifier.alpha(alphaValue),
            contentScale = scale,
            colorFilter = colorFilter,
        )
    } else {
        // 非资源名：交给 Coil 处理文件路径 / content:// / http(s)://
        AsyncImage(
            model = src,
            contentDescription = null,
            modifier = Modifier.alpha(alphaValue),
            contentScale = scale,
            colorFilter = colorFilter,
        )
    }
}

private fun parseImageContentScale(value: String?): ContentScale = when (value?.trim()?.lowercase()) {
    "cover" -> ContentScale.Crop
    "fill" -> ContentScale.FillBounds
    "fitwidth" -> ContentScale.FillWidth
    "fitheight" -> ContentScale.FillHeight
    else -> ContentScale.Fit
}

// ==================== card ====================

@Composable
private fun SlotContext.CardComponent() {
    val css = LocalComponentCss.current
    val radius = remember(css) {
        css["radius"]?.let { parseCssDp(it) }
            ?: css["border-radius"]?.let { parseCssDp(it) }
            ?: 12.dp
    }
    val defaultFill = MaterialTheme.colorScheme.surfaceContainerHigh
    val defaultStroke = MaterialTheme.colorScheme.outlineVariant
    val fill = remember(css, defaultFill) {
        css["fill-color"]?.let { parseCssColor(it) }
            ?: css["background-color"]?.let { parseCssColor(it) }
            ?: defaultFill
    }
    val strokeWidth = remember(css) { css["stroke-width"]?.let { parseCssDp(it) } ?: 0.dp }
    val strokeColor = remember(css, defaultStroke) {
        css["stroke-color"]?.let { parseCssColor(it) } ?: defaultStroke
    }
    val elevation = remember(css) { css["elevation"]?.let { parseCssDp(it) } ?: 0.dp }

    val shape = RoundedCornerShape(radius)
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 阴影必须在裁剪之前，否则会被圆角裁掉
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .clip(shape)
            .background(fill)
            .then(if (strokeWidth > 0.dp) Modifier.border(strokeWidth, strokeColor, shape) else Modifier)
    )
}

// ==================== badge ====================

@Composable
private fun SlotContext.BadgeComponent() {
    val css = LocalComponentCss.current
    val extra = LocalComponentExtra.current

    val text = (extra["content"] as? String)
        ?: css["content"]?.let { unquoteCss(it) }
        ?: ""
    if (text.isEmpty()) return

    val defaultFill = MaterialTheme.colorScheme.primary
    val defaultText = MaterialTheme.colorScheme.onPrimary
    val pill = remember(css) { cssFlag(css["pill"]) }
    val fill = remember(css, defaultFill) {
        css["fill-color"]?.let { parseCssColor(it) } ?: defaultFill
    }
    val textColor = remember(css, defaultText) {
        css["text-color"]?.let { parseCssColor(it) } ?: defaultText
    }
    val textSize = remember(css) { css["text-size"]?.let { parseCssDp(it) } ?: 12.dp }
    val padX = remember(css) { css["padding-x"]?.let { parseCssDp(it) } ?: 8.dp }
    val padY = remember(css) { css["padding-y"]?.let { parseCssDp(it) } ?: 3.dp }
    val strokeWidth = remember(css) { css["stroke-width"]?.let { parseCssDp(it) } ?: 0.dp }
    val strokeColor = remember(css) { css["stroke-color"]?.let { parseCssColor(it) } }

    val shape = if (pill) CircleShape else RoundedCornerShape(textSize.value.dp / 2f)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(fill)
            .then(
                if (strokeWidth > 0.dp && strokeColor != null) {
                    Modifier.border(strokeWidth, strokeColor, shape)
                } else Modifier
            )
            .padding(horizontal = padX, vertical = padY)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = textSize.value.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ==================== dot ====================

@Composable
private fun SlotContext.DotComponent() {
    val css = LocalComponentCss.current
    val size = remember(css) { css["size"]?.let { parseCssDp(it) } ?: 8.dp }
    val defaultDot = MaterialTheme.colorScheme.primary
    val color = remember(css, defaultDot) {
        css["color"]?.let { parseCssColor(it) } ?: defaultDot
    }
    val hollow = remember(css) { cssFlag(css["hollow"]) }
    val strokeWidth = remember(css) { css["stroke-width"]?.let { parseCssDp(it) } ?: 2.dp }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (hollow) Modifier.border(strokeWidth, color, CircleShape)
                else Modifier.background(color)
            )
    )
}

// ==================== progress（静态）====================

@Composable
private fun SlotContext.ProgressComponent() {
    val css = LocalComponentCss.current
    val extra = LocalComponentExtra.current

    val raw = (extra["value"] as? Number)?.toFloat()
        ?: css["value"]?.let { parseCssNumber(it) }
        ?: 0f
    // >1 视为百分比写法（0..100）
    val target = (if (raw > 1f) raw / 100f else raw).coerceIn(0f, 1f)

    val animate = remember(css) { cssFlag(css["animate"]) }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "css-progress",
    )
    val fraction = if (animate) animated else target

    val defaultTrack = MaterialTheme.colorScheme.surfaceVariant
    val defaultFill = MaterialTheme.colorScheme.primary
    val trackColor = remember(css, defaultTrack) {
        css["track-color"]?.let { parseCssColor(it) } ?: defaultTrack
    }
    val fillColor = remember(css, defaultFill) {
        css["fill-color"]?.let { parseCssColor(it) } ?: defaultFill
    }
    val barHeight = remember(css) { css["height"]?.let { parseCssDp(it) } ?: 4.dp }
    val radius = remember(css) { css["radius"]?.let { parseCssDp(it) } ?: 2.dp }
    val shape = RoundedCornerShape(radius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(shape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(fillColor)
        )
    }
}

// ==================== blur-layer ====================

@Composable
private fun SlotContext.BlurLayerComponent() {
    val css = LocalComponentCss.current
    val radius = remember(css) { css["blur-radius"]?.let { parseCssDp(it) } ?: 0.dp }
    val tint = remember(css) { css["tint"]?.let { parseCssColor(it) } }
    val tintAlpha = remember(css) {
        (css["tint-alpha"]?.let { parseCssNumber(it) } ?: 1f).coerceIn(0f, 1f)
    }
    val background = remember(css) { css["background"]?.let { parseCssBackgroundBrush(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 先铺底（可写 linear-gradient 形成光斑），再模糊自身绘制，最后叠色调
            .then(if (background != null) Modifier.background(background) else Modifier)
            .then(if (radius > 0.dp) Modifier.blur(radius) else Modifier)
            .then(if (tint != null) Modifier.background(tint.copy(alpha = tintAlpha)) else Modifier)
    )
}
