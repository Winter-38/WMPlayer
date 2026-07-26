package com.winter.muplayer.base_ui.ui.config

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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

/** 从 className 对应的 CSS 规则中读取 weight 值（ColumnScope / RowScope 层使用） */
fun CssRuleTable.cssWeight(className: String?): Float? {
    if (className == null) return null
    return rules[className]?.get("weight")?.toFloatOrNull()
}

// ── CSS 属性 → Compose Modifier 映射 ──

/**
 * 根据 className 从规则表中查找并应用 CSS 样式到 Modifier。
 * 当前支持的静态属性见 [applyCssProps]。
 */
fun Modifier.applyCssClass(
    className: String?,
    css: CssRuleTable,
): Modifier {
    if (className == null) return this
    val props = css.rules[className] ?: return this
    return applyCssProps(props)
}

/**
 * 将 CSS 属性字典应用到 Modifier。
 * 支持：background-color / size / width / height / border-radius /
 *       padding / opacity / scale / rotate
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

    // 5. padding
    props["padding"]?.let { padStr ->
        parseDp(padStr)?.let { p -> m = m.padding(p) }
    }

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

    return m
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
            val offsetY by transition.animateFloat(
                initialValue = 0f, targetValue = -10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(anim.durationMs / 2, easing = anim.easing.toComposeEasing()),
                    repeatMode = RepeatMode.Reverse,
                ), label = "bounce"
            )
            androidx.compose.foundation.layout.Box(
                modifier = modifier.graphicsLayer { translationY = offsetY },
                content = { content() }
            )
        }
        "fade-in" -> {
            // 一次性淡入：只在首次组合时从 0→1 动画
            val alpha by androidx.compose.runtime.produceState(initialValue = 0f) {
                // 使用 animate 不是很好，简单做法用 LaunchedEffect
            }
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

fun parseCssColor(hex: String): Color? {
    val h = hex.trimStart('#')
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
