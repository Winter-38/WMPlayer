package com.winter.muplayer.config

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 全局 CSS 规则表 —— 由 StyleConfigLoader 加载，CompositionLocal 注入。
 */
data class CssRuleTable(
    val rules: Map<String, Map<String, String>> = emptyMap(),
)

val LocalCssRules = staticCompositionLocalOf { CssRuleTable() }

/**
 * 当前组件的 CSS 属性表 —— 由 LayoutRenderer 在每个组件渲染时通过 CompositionLocal 提供。
 */
val LocalComponentCss = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

// ── CSS 属性 → Compose Modifier 映射 ──

/**
 * 将 CSS 属性字典应用到 Modifier。
 * 支持：background-color / size / width / height / border-radius /
 *       padding / padding-top/right/bottom/left / opacity / scale / rotate
 */
fun Modifier.applyCssProps(props: Map<String, String>): Modifier {
    var m = this

    // 1. background-color
    props["background-color"]?.let { hex ->
        parseCssColor(hex)?.let { color -> m = m.background(color) }
    }

    // 2. size（等宽高）
    props["size"]?.let { parseDp(it)?.let { s -> m = m.size(s) } }

    // 3. width / height
    val w = parseDp(props["width"])
    val h = parseDp(props["height"])
    if (w != null && h != null) m = m.size(w, h)
    else if (w != null) m = m.width(w)
    else if (h != null) m = m.height(h)

    // 4. border-radius
    props["border-radius"]?.let { radiusStr ->
        parseDp(radiusStr)?.let { r -> m = m.clip(RoundedCornerShape(r)) }
    }

    // 5. padding（含简写和单侧）
    m = m.applyPaddingProps(props)

    // 6. opacity
    props["opacity"]?.let { opacityStr ->
        val v = opacityStr.toFloatOrNull()
        if (v != null && v in 0f..1f) m = m.alpha(v)
    }

    // 7. scale
    props["scale"]?.let { scaleStr ->
        val v = scaleStr.toFloatOrNull()
        if (v != null && v > 0f) m = m.scale(v)
    }

    // 8. rotate (deg)
    props["rotate"]?.let { rotStr ->
        val v = parseAngle(rotStr)
        if (v != null) m = m.graphicsLayer { rotationZ = v }
    }

    // 9. overflow — 内容溢出裁剪
    props["overflow"]?.let {
        if (it == "hidden") m = m.clipToBounds()
    }

    return m
}

/**
 * 从 CSS 属性中提取 padding 值并应用到 Modifier。
 *
 * 支持格式：
 *   padding: 8px                    → 四边统一
 *   padding: 8px 16px               → 上下 8px，左右 16px
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

    // 简写 padding（1 / 2 / 4 值）
    props["padding"]?.let { padStr ->
        val parts = padStr.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        when (parts.size) {
            1 -> parseDp(parts[0])?.let { padT = it; padR = it; padB = it; padL = it }
            2 -> {
                parseDp(parts[0])?.let { padT = it; padB = it }
                parseDp(parts[1])?.let { padL = it; padR = it }
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

// ── CSS 动画 ──

/**
 * CSS animation 属性解析结果。
 * 格式：<name> <duration> [easing] [count]
 * 如：spin 3s linear infinite | pulse 2s ease-in-out 3
 */
data class CssAnimation(
    val name: String,
    val durationMs: Int = 1000,
    val easing: CssAnimationEasing = CssAnimationEasing.EASE_IN_OUT,
    val repeat: Boolean = true,
)

enum class CssAnimationEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

/** 解析 CSS animation 属性值，返回 null 表示无法解析 */
fun parseCssAnimation(value: String): CssAnimation? {
    val parts = value.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null

    val name = parts[0]
    val dur = parts.getOrNull(1)?.let { parseDurationMs(it) } ?: 1000
    val easing = parts.getOrNull(2)?.let { parseEasing(it) } ?: CssAnimationEasing.EASE_IN_OUT
    val repeat = parts.getOrNull(3) == "infinite" || parts.getOrNull(3) == null

    // spin 默认无限旋转
    val finalRepeat = if (name == "spin" && parts.lastOrNull()?.toIntOrNull() == null) true else repeat

    return CssAnimation(name, dur, easing, finalRepeat)
}

/**
 * 为组件应用 CSS animation 效果。
 * 返回一个 @Composable 闭包，接受 content 并包裹动画。
 * 如果不需要动画则返回 null。
 */
fun parseAnimationWrapper(
    props: Map<String, String>,
): (@Composable (Modifier, @Composable () -> Unit) -> Unit)? {
    val animStr = props["animation"] ?: return null
    val anim = parseCssAnimation(animStr) ?: return null

    return { modifier, content ->
        CssAnimationBox(anim, modifier, content)
    }
}

@Composable
private fun CssAnimationBox(
    anim: CssAnimation,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "css_${anim.name}")

    when (anim.name) {
        "spin" -> {
            val rotation by transition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(anim.durationMs, easing = anim.easing.toComposeEasing()),
                    repeatMode = RepeatMode.Restart,
                ), label = "spin"
            )
            androidx.compose.foundation.layout.Box(
                modifier = modifier.graphicsLayer { rotationZ = rotation },
                content = { content() }
            )
        }
        "pulse" -> {
            val s by transition.animateFloat(
                initialValue = 1f, targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(anim.durationMs / 2, easing = anim.easing.toComposeEasing()),
                    repeatMode = RepeatMode.Reverse,
                ), label = "pulse"
            )
            androidx.compose.foundation.layout.Box(
                modifier = modifier.scale(s),
                content = { content() }
            )
        }
        "bounce" -> {
            val bounceY by transition.animateFloat(
                initialValue = 0f, targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(anim.durationMs / 2, easing = anim.easing.toComposeEasing()),
                    repeatMode = RepeatMode.Reverse,
                ), label = "bounce"
            )
            androidx.compose.foundation.layout.Box(
                modifier = modifier.graphicsLayer { translationY = bounceY },
                content = { content() }
            )
        }
        "fade-in" -> {
            val animatedAlpha = androidx.compose.animation.core.Animatable(0f)
            androidx.compose.runtime.LaunchedEffect(Unit) {
                animatedAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(anim.durationMs, easing = anim.easing.toComposeEasing())
                )
            }
            androidx.compose.foundation.layout.Box(
                modifier = modifier.alpha(animatedAlpha.value),
                content = { content() }
            )
        }
        else -> content()
    }
}

private fun CssAnimationEasing.toComposeEasing(): androidx.compose.animation.core.Easing = when (this) {
    CssAnimationEasing.LINEAR -> LinearEasing
    CssAnimationEasing.EASE_IN -> androidx.compose.animation.core.FastOutSlowInEasing
    CssAnimationEasing.EASE_OUT -> androidx.compose.animation.core.FastOutSlowInEasing
    CssAnimationEasing.EASE_IN_OUT -> androidx.compose.animation.core.FastOutSlowInEasing
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

private fun parseDurationMs(value: String): Int {
    val trimmed = value.trim()
    return when {
        trimmed.endsWith("ms") -> trimmed.removeSuffix("ms").trim().toFloatOrNull()?.toInt() ?: 1000
        trimmed.endsWith("s") -> (trimmed.removeSuffix("s").trim().toFloatOrNull()?.times(1000f)?.toInt()) ?: 1000
        else -> trimmed.toIntOrNull() ?: 1000
    }
}

private fun parseEasing(value: String): CssAnimationEasing? = when (value) {
    "linear" -> CssAnimationEasing.LINEAR
    "ease-in" -> CssAnimationEasing.EASE_IN
    "ease-out" -> CssAnimationEasing.EASE_OUT
    "ease-in-out" -> CssAnimationEasing.EASE_IN_OUT
    else -> null
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
