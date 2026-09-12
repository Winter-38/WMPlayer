package com.winter.muplayer.config

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CSS 样式值解析器（文本 / 阴影 / 边框 / 渐变）。
 *
 * 与 [CssEngine] 中的通用解析（颜色、长度）拆分存放：这里只放"复合值"解析，
 * 供组件（text / image / divider / card 等）与 [Modifier.applyCssProps] 共用。
 * 所有函数在无法解析时返回 null，由调用方回退主题默认值。
 */

// ── 文本 ──

/**
 * 字体族：`serif` / `monospace` / `cursive` / `sans-serif`。
 * 未知值返回 null（使用主题默认字体）。
 */
fun parseCssFontFamily(value: String?): FontFamily? = when (value?.trim()?.lowercase()) {
    "serif" -> FontFamily.Serif
    "monospace", "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    "sans-serif", "sans" -> FontFamily.SansSerif
    else -> null
}

/**
 * 行高：`1.5`（相对字号的倍数）/ `24px`（绝对长度）/ `normal`（null = 主题默认）。
 * 倍数形式需要 [fontSize] 作基准；基准缺失时返回 null。
 */
fun parseCssLineHeight(value: String?, fontSize: TextUnit?): TextUnit? {
    val raw = value?.trim()?.lowercase() ?: return null
    if (raw == "normal" || raw.isEmpty()) return null
    val isRelative = !(raw.endsWith("px") || raw.endsWith("dp") || raw.endsWith("sp"))
    return if (isRelative) {
        val multiple = raw.toFloatOrNull() ?: return null
        val base = fontSize?.takeIf { it.isSp }?.value ?: return null
        (base * multiple).sp
    } else {
        val absolute = parseCssPxFloat(raw) ?: return null
        absolute.sp
    }
}

/** 字间距：`2px` / `0.5`（同 dp 语义）→ sp；无效 → null */
fun parseCssLetterSpacing(value: String?): TextUnit? {
    val raw = value?.trim() ?: return null
    return parseCssPxFloat(raw)?.sp
}

/**
 * 大小写变换：`uppercase` / `lowercase` / `capitalize` / `none`。
 *
 * 不依赖 Compose API：当前 Compose 版本已移除 `TextTransform`（实测 `androidx.compose.ui.text.style.TextTransform`
 * 类不存在），因此改为对字符串施加变换，由文本组件在渲染前调用。
 */
enum class CssTextTransform { UPPERCASE, LOWERCASE, CAPITALIZE, NONE }

/** 大小写变换：`uppercase` / `lowercase` / `capitalize` / `none`；未知值返回 null（保持原文） */
fun parseCssTextTransform(value: String?): CssTextTransform? = when (value?.trim()?.lowercase()) {
    "uppercase" -> CssTextTransform.UPPERCASE
    "lowercase" -> CssTextTransform.LOWERCASE
    "capitalize" -> CssTextTransform.CAPITALIZE
    "none" -> CssTextTransform.NONE
    else -> null
}

/** 对文本施加大小写变换 */
fun CssTextTransform.applyTo(text: String): String = when (this) {
    CssTextTransform.UPPERCASE -> text.uppercase()
    CssTextTransform.LOWERCASE -> text.lowercase()
    CssTextTransform.CAPITALIZE -> text.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
    }
    CssTextTransform.NONE -> text
}

/** 文本装饰：`underline` / `line-through` / `none`（可组合，空格分隔） */
fun parseCssTextDecoration(value: String?): TextDecoration? {
    val raw = value?.trim()?.lowercase() ?: return null
    if (raw.isEmpty()) return null
    if (raw == "none") return TextDecoration.None
    var result: TextDecoration? = null
    if (raw.contains("underline")) result = TextDecoration.Underline
    if (raw.contains("line-through")) {
        result = result?.plus(TextDecoration.LineThrough) ?: TextDecoration.LineThrough
    }
    // 注：Compose 的 TextDecoration 无 Overline（实测当前版本已移除），故不支持 overline
    return result
}

/**
 * 文本最大行数：`2` / `none`（不限行）。
 * 无效值返回 null（调用方用自身默认值）。
 */
fun parseCssMaxLines(value: String?): Int? {
    val raw = value?.trim()?.lowercase() ?: return null
    if (raw == "none" || raw == "unlimited") return Int.MAX_VALUE
    return raw.toIntOrNull()?.takeIf { it > 0 }
}

/**
 * 文本溢出：`ellipsis` / `clip` / `visible`。
 * 返回 null 表示未指定（调用方用自身默认值）。
 */
fun parseCssTextOverflow(value: String?): androidx.compose.ui.text.style.TextOverflow? =
    when (value?.trim()?.lowercase()) {
        "ellipsis" -> androidx.compose.ui.text.style.TextOverflow.Ellipsis
        "clip" -> androidx.compose.ui.text.style.TextOverflow.Clip
        "visible" -> androidx.compose.ui.text.style.TextOverflow.Visible
        else -> null
    }

// ── 阴影与边框 ──

/**
 * 阴影参数（box-shadow / text-shadow 共用）。
 * @param dx 水平偏移（px）
 * @param dy 垂直偏移（px）
 * @param blur 模糊半径（px）
 * @param spread 扩散（px，仅 box-shadow 使用）
 */
data class CssShadow(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val blur: Float = 0f,
    val spread: Float = 0f,
    val color: Color = Color(0x66000000),
)

/**
 * 解析 `dx dy [blur] [spread] [color]`（顺序自由，颜色可出现在任意位置）。
 * `none` / 无有效数值 → null。
 */
fun parseCssShadow(value: String?): CssShadow? {
    val raw = value?.trim() ?: return null
    if (raw.isEmpty() || raw.equals("none", ignoreCase = true)) return null

    var color: Color? = null
    val lengths = mutableListOf<Float>()
    for (token in raw.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        val parsedColor = parseCssColor(token)
        if (parsedColor != null) {
            color = parsedColor
            continue
        }
        val parsedLength = parseCssPxFloat(token) ?: continue
        lengths.add(parsedLength)
    }
    if (lengths.isEmpty()) return null
    return CssShadow(
        dx = lengths.getOrElse(0) { 0f },
        dy = lengths.getOrElse(1) { 0f },
        blur = lengths.getOrElse(2) { 0f },
        spread = lengths.getOrElse(3) { 0f },
        color = color ?: Color(0x66000000),
    )
}

/** 边框（border: `2px solid #ffffff`；宽度与颜色顺序自由，线型仅接受 solid/dashed 的宽度语义） */
data class CssBorder(val width: Dp, val color: Color)

fun parseCssBorder(value: String?, defaultColor: Color = Color(0x33FFFFFF)): CssBorder? {
    val raw = value?.trim() ?: return null
    if (raw.isEmpty() || raw.equals("none", ignoreCase = true)) return null
    var width: Dp? = null
    var color: Color? = null
    for (token in raw.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        val parsedColor = parseCssColor(token)
        if (parsedColor != null) {
            color = parsedColor
            continue
        }
        parseCssPxFloat(token)?.let { width = it.dp }
    }
    return CssBorder(width = width ?: 1.dp, color = color ?: defaultColor)
}

// ── 渐变 ──

/**
 * 线性渐变画刷 —— `linear-gradient(180deg, #fff, #000)` / `linear-gradient(to bottom, ...)`。
 *
 * 角度采用 CSS 语义（0deg = 自下向上，顺时针增加）；在 [createShader] 时按实际尺寸
 * 计算渐变端点，使其铺满整个绘制区域。
 */
class CssLinearGradientBrush(
    private val angleDeg: Float,
    private val colors: List<Color>,
) : ShaderBrush() {

    override fun createShader(size: Size): android.graphics.Shader {
        val radians = Math.toRadians((angleDeg - 90f).toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        // 渐变轴半长：覆盖整个矩形所需的最短距离
        val half = kotlin.math.abs(halfW * cos) + kotlin.math.abs(halfH * sin)
        val cx = halfW
        val cy = halfH
        return android.graphics.LinearGradient(
            cx - cos * half,
            cy - sin * half,
            cx + cos * half,
            cy + sin * half,
            colors.map { it.toArgb() }.toIntArray(),
            null,
            android.graphics.Shader.TileMode.CLAMP,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is CssLinearGradientBrush && other.angleDeg == angleDeg && other.colors == colors

    override fun hashCode(): Int = 31 * angleDeg.hashCode() + colors.hashCode()
}

/**
 * 解析线性渐变；非渐变或缺色标 → null。
 * 支持首段为角度（`180deg`）或方向关键字（`to bottom` / `to right` 等）。
 */
fun parseCssLinearGradient(value: String?): Brush? {
    val raw = value?.trim() ?: return null
    if (!raw.startsWith("linear-gradient(", ignoreCase = true)) return null
    val inner = raw.substringAfter('(').substringBeforeLast(')')
    val parts = inner.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null

    var angleDeg = 180f // CSS 默认 to bottom
    val colors = mutableListOf<Color>()

    for ((index, part) in parts.withIndex()) {
        if (index == 0) {
            val direction = parseGradientDirection(part)
            if (direction != null) {
                angleDeg = direction
                continue
            }
        }
        parseCssColor(part)?.let { colors.add(it) }
    }
    if (colors.size < 2) return null
    return CssLinearGradientBrush(angleDeg, colors)
}

/** 渐变方向 → CSS 角度；非方向 → null */
private fun parseGradientDirection(token: String): Float? {
    val t = token.trim().lowercase()
    if (t.endsWith("deg")) return t.removeSuffix("deg").trim().toFloatOrNull()
    return when (t) {
        "to top" -> 0f
        "to right" -> 90f
        "to bottom" -> 180f
        "to left" -> 270f
        "to top right", "to right top" -> 45f
        "to bottom right", "to right bottom" -> 135f
        "to bottom left", "to left bottom" -> 225f
        "to top left", "to left top" -> 315f
        else -> null
    }
}

/** 背景属性：同时接受纯色（`#fff`）与线性渐变 */
fun parseCssBackgroundBrush(value: String?): Brush? {
    val raw = value?.trim() ?: return null
    parseCssLinearGradient(raw)?.let { return it }
    parseCssColor(raw)?.let { return androidx.compose.ui.graphics.SolidColor(it) }
    return null
}
