package com.winter.muplayer.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局 CSS 规则表 —— 由 StyleConfigLoader 加载，CompositionLocal 注入。
 *
 * [keyframes] 存放 `@keyframes` 定义，供 `animation` / `enter` 属性按名引用；
 * 与 [rules] 分开存放，避免选择器查找时误匹配到动画名。
 */
data class CssRuleTable(
    val rules: Map<String, Map<String, String>> = emptyMap(),
    val keyframes: Map<String, CssKeyframes> = emptyMap(),
)

val LocalCssRules = staticCompositionLocalOf { CssRuleTable() }

/**
 * 当前组件的 CSS 属性表 —— 由 LayoutRenderer 在每个组件渲染时通过 CompositionLocal 提供。
 */
val LocalComponentCss = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

// ── CSS 属性 → Compose Modifier 映射 ──

/**
 * 将 CSS 属性字典应用到 Modifier。
 *
 * 应用顺序（自外向内）：尺寸 → 约束 → 阴影 → 背景 → 边框 → 裁剪 → 变换 → 内边距。
 *
 * 支持：
 * - 尺寸 `size` / `width` / `height` / `min-width` / `min-height` / `max-width` / `max-height`
 * - 背景 `background-color`（纯色）/ `background`（纯色或 `linear-gradient(...)`）
 * - 边框 `border`（`1px solid #fff`，宽度与颜色顺序自由）/ `border-radius`
 * - 阴影 `box-shadow`（`0 4px 12px rgba(0,0,0,.3)`）
 * - 内边距 `padding` / `padding-top|right|bottom|left`
 * - 透明度 `opacity`，变换 `scale`（1 或 2 值）/ `rotate`
 * - 裁剪 `overflow: hidden`
 *
 * 其中 `scale` / `rotate` / `opacity` 只作用于绘制层（graphicsLayer），不改变布局占位。
 * 无法识别的属性与无法解析的值被静默忽略（保持原有宽容行为）。
 */
fun Modifier.applyCssProps(props: Map<String, String>): Modifier {
    var m = this

    // 1. 尺寸
    props["size"]?.let { parseDp(it)?.let { s -> m = m.size(s) } }
    val w = parseDp(props["width"])
    val h = parseDp(props["height"])
    when {
        w != null && h != null -> m = m.size(w, h)
        w != null -> m = m.width(w)
        h != null -> m = m.height(h)
    }

    // 2. 尺寸约束
    val minW = parseDp(props["min-width"])
    val minH = parseDp(props["min-height"])
    val maxW = parseDp(props["max-width"])
    val maxH = parseDp(props["max-height"])
    if (minW != null || maxW != null) {
        m = m.widthIn(min = minW ?: Dp.Unspecified, max = maxW ?: Dp.Unspecified)
    }
    if (minH != null || maxH != null) {
        m = m.heightIn(min = minH ?: Dp.Unspecified, max = maxH ?: Dp.Unspecified)
    }

    // 3. 圆角形状（背景 / 边框 / 裁剪共用）
    val radius = props["border-radius"]?.let { parseDp(it) }
    val shape: Shape = if (radius != null && radius.value > 0f) RoundedCornerShape(radius) else RectangleShape

    // 4. 阴影（在裁剪之前，否则会被圆角裁掉）
    parseCssShadow(props["box-shadow"])?.let { shadow -> m = m.cssDropShadow(shadow, shape) }

    // 5. 背景：background 优先（支持渐变），background-color 作为纯色回退
    val brush = parseCssBackgroundBrush(props["background"])
        ?: props["background-color"]?.let { parseCssColor(it) }?.let { SolidColor(it) }
    if (brush != null) m = m.background(brush, shape)

    // 6. 边框
    parseCssBorder(props["border"])?.let { b -> m = m.border(b.width, b.color, shape) }

    // 7. 裁剪
    if (radius != null && radius.value > 0f) m = m.clip(shape)
    props["overflow"]?.let { if (it == "hidden") m = m.clipToBounds() }

    // 8. 透明度 / 变换（仅绘制层）
    props["opacity"]?.let { opacityStr ->
        val v = opacityStr.toFloatOrNull()
        if (v != null && v in 0f..1f) m = m.alpha(v)
    }
    props["scale"]?.let { scaleStr ->
        val values = scaleStr.split(" ").mapNotNull { it.trim().toFloatOrNull() }
        when {
            values.size == 1 && values[0] > 0f -> m = m.scale(values[0])
            values.size >= 2 -> m = m.graphicsLayer {
                scaleX = values[0]
                scaleY = values[1]
            }
        }
    }
    props["rotate"]?.let { rotStr ->
        parseCssAngle(rotStr)?.let { v -> m = m.graphicsLayer { rotationZ = v } }
    }

    // 9. 内边距（收在最内层）
    m = m.applyPaddingProps(props)

    return m
}

/**
 * `box-shadow` → Modifier.shadow 的**近似**映射。
 *
 * Compose 的 shadow 只接受 elevation，无法精确表达 CSS 的 blur / spread / 偏移方向；
 * 这里用 blur 与向下偏移推导 elevation，颜色沿用 CSS 颜色（API 28+ 生效）。
 * 如需像素级精确阴影，应在组件内自绘。
 */
internal fun Modifier.cssDropShadow(shadow: CssShadow, shape: Shape): Modifier {
    val elevationPx = maxOf(shadow.blur / 2f, shadow.dy.coerceAtLeast(0f))
    if (elevationPx <= 0f) return this
    return this.shadow(
        elevation = elevationPx.coerceIn(0f, 48f).dp,
        shape = shape,
        clip = false,
        ambientColor = shadow.color,
        spotColor = shadow.color,
    )
}

/**
 * 从 CSS 属性中提取 padding 值并应用到 Modifier。
 *
 * 支持格式：
 *   padding: 8px                    → 四边统一
 *   padding: 8px 16px               → 上下 8px，左右 16px
 *   padding: 8px 16px 12px          → 上 8px，左右 16px，下 12px
 *   padding: 8px 12px 16px 20px     → 上 8px，右 12px，下 16px，左 20px
 *   padding-top: 8px                → 单独覆盖上边
 *   padding-right: 12px
 *   padding-bottom: 16px
 *   padding-left: 20px
 *
 * 简写和单侧可同时使用，单侧值覆盖简写中的对应边。
 * 单侧属性不再额外叠加简写值。
 */
fun Modifier.applyPaddingProps(props: Map<String, String>): Modifier {
    var padT = 0.dp; var padR = 0.dp; var padB = 0.dp; var padL = 0.dp

    // 简写 padding（1 / 2 / 3 / 4 值）
    props["padding"]?.let { padStr ->
        val parts = padStr.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        when (parts.size) {
            1 -> parseDp(parts[0])?.let { padT = it; padR = it; padB = it; padL = it }
            2 -> {
                parseDp(parts[0])?.let { padT = it; padB = it }
                parseDp(parts[1])?.let { padL = it; padR = it }
            }
            3 -> {
                parseDp(parts[0])?.let { padT = it }
                parseDp(parts[1])?.let { padL = it; padR = it }
                parseDp(parts[2])?.let { padB = it }
            }
            4 -> {
                parseDp(parts[0])?.let { padT = it }
                parseDp(parts[1])?.let { padR = it }
                parseDp(parts[2])?.let { padB = it }
                parseDp(parts[3])?.let { padL = it }
            }
        }
    }

    // 单侧 padding 覆盖简写值
    props["padding-top"]?.let { parseDp(it)?.let { padT = it } }
    props["padding-right"]?.let { parseDp(it)?.let { padR = it } }
    props["padding-bottom"]?.let { parseDp(it)?.let { padB = it } }
    props["padding-left"]?.let { parseDp(it)?.let { padL = it } }

    return if (padT > 0.dp || padR > 0.dp || padB > 0.dp || padL > 0.dp)
        this.padding(PaddingValues(start = padL, top = padT, end = padR, bottom = padB))
    else this
}


// ── 值解析工具 ──

private fun parseDp(value: String?): androidx.compose.ui.unit.Dp? {
    if (value == null) return null
    val trimmed = value.trim()
    val num = when {
        trimmed.endsWith("px") -> trimmed.removeSuffix("px").trim().toFloatOrNull()
        trimmed.endsWith("dp") -> trimmed.removeSuffix("dp").trim().toFloatOrNull()
        else -> trimmed.toFloatOrNull()
    }
    return num?.let { it.dp }
}

/** 常用 CSS 颜色名 → ARGB 值 */
private val NAMED_COLORS: Map<String, Long> = mapOf(
    "red" to 0xFFFF0000, "green" to 0xFF008000, "blue" to 0xFF0000FF,
    "white" to 0xFFFFFFFF, "black" to 0xFF000000,
    "gray" to 0xFF808080, "grey" to 0xFF808080,
    "yellow" to 0xFFFFFF00, "orange" to 0xFFFFA500, "purple" to 0xFF800080,
    "pink" to 0xFFFFC0CB, "cyan" to 0xFF00FFFF, "magenta" to 0xFFFF00FF,
    "brown" to 0xFFA52A2A, "navy" to 0xFF000080, "teal" to 0xFF008080,
    "lime" to 0xFF00FF00, "maroon" to 0xFF800000, "olive" to 0xFF808000,
    "silver" to 0xFFC0C0C0, "gold" to 0xFFFFD700, "indigo" to 0xFF4B0082,
    "violet" to 0xFFEE82EE, "salmon" to 0xFFFA8072, "coral" to 0xFFFF7F50,
    "tomato" to 0xFFFF6347, "skyblue" to 0xFF87CEEB,
    "lightgray" to 0xFFD3D3D3, "darkgray" to 0xFFA9A9A9,
    "transparent" to 0x00000000,
)

/**
 * 解析 CSS 颜色值。
 * 支持：
 * - `#RGB` / `#RRGGBB` / `#AARRGGBB`（hex）
 * - `rgb(r, g, b)`（0-255 或百分比）
 * - `rgba(r, g, b, a)`（a 为 0-1 或百分比）
 * - 颜色名（red / white / black / blue 等）
 */
fun parseCssColor(value: String): Color? {
    val trimmed = value.trim()

    // 命名颜色（大小写不敏感）
    NAMED_COLORS[trimmed.lowercase()]?.let { return Color(it) }

    val h = trimmed.trimStart('#')

    // 函数格式：rgb(...) / rgba(...)
    if (h.startsWith("rgb(") || h.startsWith("rgba(")) {
        val hasAlpha = h.startsWith("rgba(")
        val inner = h.substringAfter('(').substringBeforeLast(')').trim()
        if (inner.isEmpty()) return null
        val parts = inner.split(',').map { it.trim() }
        if (parts.size != if (hasAlpha) 4 else 3) return null

        fun channel(raw: String): Float? {
            val v = raw.removeSuffix("%").trim().toFloatOrNull() ?: return null
            return if (raw.trim().endsWith("%")) (v / 100f) * 255f else v
        }
        val r = channel(parts[0]) ?: return null
        val g = channel(parts[1]) ?: return null
        val b = channel(parts[2]) ?: return null
        val a = if (hasAlpha) {
            val rawA = parts[3].trim()
            val av = rawA.removeSuffix("%").trim().toFloatOrNull() ?: return null
            if (rawA.endsWith("%")) av / 100f else av
        } else 1f
        return Color(r / 255f, g / 255f, b / 255f, a.coerceIn(0f, 1f))
    }

    if (h.length == 3) {
        // #RGB 简写：每位重复一次（此前实现只接受 6/8 位，与注释声称的支持不符）
        val expanded = buildString {
            h.forEach { c -> append(c).append(c) }
        }
        val colorLong = expanded.toLongOrNull(16) ?: return null
        return Color(0xFF000000 or colorLong)
    }
    if (h.length != 6 && h.length != 8) return null
    val colorLong = h.toLongOrNull(16) ?: return null
    return if (h.length == 8) Color(colorLong)
    else Color(0xFF000000 or colorLong)
}

/** 解析 CSS 长度值：`24px` → 24.dp，`16dp` → 16.dp，纯数字 → 同值 dp */
fun parseCssDp(value: String): androidx.compose.ui.unit.Dp {
    val trimmed = value.trim()
    val num = when {
        trimmed.endsWith("px") -> trimmed.removeSuffix("px").trim().toFloatOrNull()
        trimmed.endsWith("dp") -> trimmed.removeSuffix("dp").trim().toFloatOrNull()
        else -> trimmed.toFloatOrNull()
    }
    return (num ?: 0f).dp
}

/** 解析 CSS 长度值为 Float px：`24px`/`16dp` → 24f/16f，纯数字 → 同值；无法解析 → null。
 *  （供液态玻璃等需要原始 px 数值、而非 Compose Dp 的属性使用。） */
fun parseCssPxFloat(value: String?): Float? {
    if (value == null) return null
    val trimmed = value.trim()
    return when {
        trimmed.endsWith("px") -> trimmed.removeSuffix("px").trim().toFloatOrNull()
        trimmed.endsWith("dp") -> trimmed.removeSuffix("dp").trim().toFloatOrNull()
        else -> trimmed.toFloatOrNull()
    }
}

/** 解析 CSS 无单位数值：`0.55` → 0.55f；无法解析 → null。 */
fun parseCssNumber(value: String?): Float? {
    if (value == null) return null
    return value.trim().toFloatOrNull()
}

/** 解析角度值，支持 deg 单位 */
private fun parseAngle(value: String): Float? {
    val trimmed = value.trim()
    return when {
        trimmed.endsWith("deg") -> trimmed.removeSuffix("deg").trim().toFloatOrNull()
        else -> trimmed.toFloatOrNull()
    }
}

/** 解析 CSS align-self 值（Row 内），null = stretch 或无效值。 */
fun parseAlignSelfRow(value: String?): Alignment.Vertical? {
    return when (value) {
        "start"  -> Alignment.Top
        "center" -> Alignment.CenterVertically
        "end"    -> Alignment.Bottom
        else     -> null
    }
}

/** 解析 CSS align-self 值（Column 内），null = stretch 或无效值。 */
fun parseAlignSelfColumn(value: String?): Alignment.Horizontal? {
    return when (value) {
        "start"  -> Alignment.Start
        "center" -> Alignment.CenterHorizontally
        "end"    -> Alignment.End
        else     -> null
    }
}

// ── justify-content 解析 ──

/** 解析 CSS justify-content 值（Row 内），null = 无效值。 */
fun parseJustifyContent(value: String?): Arrangement.Horizontal? {
    return when (value) {
        "start"         -> Arrangement.Start
        "center"        -> Arrangement.Center
        "end"           -> Arrangement.End
        "space-between" -> Arrangement.SpaceBetween
        "space-evenly"  -> Arrangement.SpaceEvenly
        "space-around"  -> Arrangement.SpaceAround
        else            -> null
    }
}

/** 解析 CSS justify-content 值（Column 内），null = 无效值。 */
fun parseJustifyContentVertical(value: String?): Arrangement.Vertical? {
    return when (value) {
        "start"         -> Arrangement.Top
        "center"        -> Arrangement.Center
        "end"           -> Arrangement.Bottom
        "space-between" -> Arrangement.SpaceBetween
        "space-evenly"  -> Arrangement.SpaceEvenly
        "space-around"  -> Arrangement.SpaceAround
        else            -> null
    }
}

/**
 * justify-content → Alignment.Horizontal，用于和 gap 组合。
 * start/center/end 才有对应 Alignment，其他返回 null。
 */
fun parseJustifyAlignment(value: String?): Alignment.Horizontal? {
    return when (value) {
        "start"  -> Alignment.Start
        "center" -> Alignment.CenterHorizontally
        "end"    -> Alignment.End
        else     -> null
    }
}

/**
 * justify-content → Alignment.Vertical，用于和 gap 组合。
 */
fun parseJustifyAlignmentVertical(value: String?): Alignment.Vertical? {
    return when (value) {
        "start"  -> Alignment.Top
        "center" -> Alignment.CenterVertically
        "end"    -> Alignment.Bottom
        else     -> null
    }
}

/** 解析 CSS content-align 属性值，用于组件渲染 Box 的内容对齐。 */
fun parseContentAlign(value: String?): Alignment? {
    return when (value) {
        "start"  -> Alignment.TopStart
        "center" -> Alignment.Center
        "end"    -> Alignment.BottomEnd
        else     -> null
    }
}
