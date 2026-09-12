package com.winter.muplayer.config

/**
 * CSS 关键帧动画 —— 数据模型与解析。
 *
 * ## 设计约束（重要）
 * 所有动画属性都只作用于**绘制层**（graphicsLayer：平移 / 缩放 / 旋转 / 透明度），
 * 不参与测量与布局。因此动画期间组件/slot 的**理论存在区域（布局占位）完全不变**，
 * 相邻元素不会被推动 —— 这正是界面切换过渡所需要的语义。
 *
 * ## 支持的关键帧属性
 * - `opacity`                   0..1
 * - `translate-x` / `translate-y`   长度（px / dp / 纯数字，语义同 dp）
 * - `translate`                 1 值（x 轴）或 2 值（x y）
 * - `scale`                     1 值（等比）或 2 值（x y）
 * - `scale-x` / `scale-y`
 * - `rotate`                   绕 Z 轴（平面内）旋转，角度（deg / turn / rad / 纯数字）
 * - `rotate-x` / `rotate-y`    绕 X / Y 轴 3D 旋转（翻转入场用）
 * - `rotate`                    角度（deg / 纯数字）
 *
 * ## 语法
 * ```css
 * @keyframes fade-up {
 *   from { opacity: 0; translate-y: 16px; }
 *   to   { opacity: 1; translate-y: 0; }
 * }
 *
 * #app-name { enter: fade-up 320ms ease-out; }
 * .app-top  { enter: fade-up 300ms ease-out; stagger: 60ms; }
 * ```
 * 帧选择器支持 `from` / `to` / `N%` / 逗号列表（`0%, 100%`）。
 * 未在某帧声明的通道不参与该帧，按通道独立插值；整组都未声明的通道取默认值。
 */
data class CssKeyframe(
    val offset: Float,
    val opacity: Float? = null,
    val translateX: Float? = null,
    val translateY: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    /** 绕 Z 轴旋转（平面内），单位度 */
    val rotate: Float? = null,
    /** 绕 X 轴旋转（3D 上下翻转），单位度 */
    val rotateX: Float? = null,
    /** 绕 Y 轴旋转（3D 左右翻转），单位度 */
    val rotateY: Float? = null,
)

/** 某一时刻的绘制参数（已解析为最终值，默认值 = 无动画时的状态） */
data class KeyframeValues(
    val alpha: Float = 1f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationZ: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
) {
    companion object {
        val Identity = KeyframeValues()
    }
}

/**
 * 一组关键帧 —— 按通道独立插值。
 *
 * [sample] 在 [0,1] 进度上求值：超出范围取端点，区间内线性插值。
 * 单帧时该帧值即常量（不随进度变化）。
 */
class CssKeyframes(
    val name: String,
    val frames: List<CssKeyframe>,
) {
    /** 每个通道的采样点（按 offset 升序）；空 = 该通道不参与动画 */
    private val alphaTrack: List<Pair<Float, Float>> = frames.track { it.opacity }
    private val translateXTrack: List<Pair<Float, Float>> = frames.track { it.translateX }
    private val translateYTrack: List<Pair<Float, Float>> = frames.track { it.translateY }
    private val scaleXTrack: List<Pair<Float, Float>> = frames.track { it.scaleX }
    private val scaleYTrack: List<Pair<Float, Float>> = frames.track { it.scaleY }
    private val rotateTrack: List<Pair<Float, Float>> = frames.track { it.rotate }
    private val rotateXTrack: List<Pair<Float, Float>> = frames.track { it.rotateX }
    private val rotateYTrack: List<Pair<Float, Float>> = frames.track { it.rotateY }

    val isEmpty: Boolean get() = frames.isEmpty()

    fun sample(t: Float): KeyframeValues {
        val p = t.coerceIn(0f, 1f)
        return KeyframeValues(
            alpha = sampleTrack(alphaTrack, p, 1f),
            translateX = sampleTrack(translateXTrack, p, 0f),
            translateY = sampleTrack(translateYTrack, p, 0f),
            scaleX = sampleTrack(scaleXTrack, p, 1f),
            scaleY = sampleTrack(scaleYTrack, p, 1f),
            rotationZ = sampleTrack(rotateTrack, p, 0f),
            rotationX = sampleTrack(rotateXTrack, p, 0f),
            rotationY = sampleTrack(rotateYTrack, p, 0f),
        )
    }

    // ── 逐通道求值（绘制回调内逐帧调用，返回基本类型、零分配） ──

    fun alphaAt(t: Float): Float = sampleTrack(alphaTrack, t.coerceIn(0f, 1f), 1f)
    fun translateXAt(t: Float): Float = sampleTrack(translateXTrack, t.coerceIn(0f, 1f), 0f)
    fun translateYAt(t: Float): Float = sampleTrack(translateYTrack, t.coerceIn(0f, 1f), 0f)
    fun scaleXAt(t: Float): Float = sampleTrack(scaleXTrack, t.coerceIn(0f, 1f), 1f)
    fun scaleYAt(t: Float): Float = sampleTrack(scaleYTrack, t.coerceIn(0f, 1f), 1f)
    fun rotationAt(t: Float): Float = sampleTrack(rotateTrack, t.coerceIn(0f, 1f), 0f)
    fun rotationXAt(t: Float): Float = sampleTrack(rotateXTrack, t.coerceIn(0f, 1f), 0f)
    fun rotationYAt(t: Float): Float = sampleTrack(rotateYTrack, t.coerceIn(0f, 1f), 0f)

    override fun equals(other: Any?): Boolean =
        this === other || (other is CssKeyframes && name == other.name && frames == other.frames)

    override fun hashCode(): Int = 31 * name.hashCode() + frames.hashCode()

    override fun toString(): String = "CssKeyframes($name, ${frames.size} frames)"

    private companion object {
        fun List<CssKeyframe>.track(selector: (CssKeyframe) -> Float?): List<Pair<Float, Float>> =
            mapNotNull { frame -> selector(frame)?.let { frame.offset to it } }
                .sortedBy { it.first }

        /** 通道求值：空轨道 → 默认值；单点 → 常量；区间 → 线性插值 */
        fun sampleTrack(track: List<Pair<Float, Float>>, p: Float, fallback: Float): Float {
            if (track.isEmpty()) return fallback
            if (track.size == 1) return track[0].second
            if (p <= track.first().first) return track.first().second
            if (p >= track.last().first) return track.last().second
            for (i in 0 until track.size - 1) {
                val (o0, v0) = track[i]
                val (o1, v1) = track[i + 1]
                if (p in o0..o1) {
                    if (o1 - o0 <= 1e-6f) return v1
                    val k = (p - o0) / (o1 - o0)
                    return v0 + (v1 - v0) * k
                }
            }
            return track.last().second
        }
    }
}

/**
 * 内置动画预设 —— 与用户 `@keyframes` 同名时，用户定义优先（可覆盖内置）。
 * 全部只改绘制参数，不动布局。
 */
object BuiltinKeyframes {

    private val presets: Map<String, CssKeyframes> = listOf(
        // ── 入场类：淡入 ──
        kf("fade-in") { f(0f, alpha = 0f); f(1f, alpha = 1f) },
        kf("fade-up") { f(0f, alpha = 0f, ty = 16f); f(1f, alpha = 1f, ty = 0f) },
        kf("fade-down") { f(0f, alpha = 0f, ty = -16f); f(1f, alpha = 1f, ty = 0f) },
        kf("fade-left") { f(0f, alpha = 0f, tx = 16f); f(1f, alpha = 1f, tx = 0f) },
        kf("fade-right") { f(0f, alpha = 0f, tx = -16f); f(1f, alpha = 1f, tx = 0f) },
        // ── 入场类：滑入 ──
        kf("slide-in-left") { f(0f, alpha = 0f, tx = -40f); f(1f, alpha = 1f, tx = 0f) },
        kf("slide-in-right") { f(0f, alpha = 0f, tx = 40f); f(1f, alpha = 1f, tx = 0f) },
        kf("slide-in-up") { f(0f, alpha = 0f, ty = 40f); f(1f, alpha = 1f, ty = 0f) },
        kf("slide-in-down") { f(0f, alpha = 0f, ty = -40f); f(1f, alpha = 1f, ty = 0f) },
        // ── 入场类：缩放 ──
        kf("zoom-in") { f(0f, alpha = 0f, sx = 0.86f, sy = 0.86f); f(1f, alpha = 1f, sx = 1f, sy = 1f) },
        kf("zoom-out") { f(0f, alpha = 0f, sx = 1.14f, sy = 1.14f); f(1f, alpha = 1f, sx = 1f, sy = 1f) },
        kf("pop") {
            f(0f, sx = 0.6f, sy = 0.6f)
            f(0.6f, sx = 1.06f, sy = 1.06f)
            f(1f, sx = 1f, sy = 1f)
        },
        // ── 入场类：旋转 / 翻转 / 落下 ──
        kf("rotate-in") {
            f(0f, alpha = 0f, rot = -180f, sx = 0.7f, sy = 0.7f)
            f(1f, alpha = 1f, rot = 0f, sx = 1f, sy = 1f)
        },
        kf("rotate-in-cw") {
            f(0f, alpha = 0f, rot = 180f, sx = 0.7f, sy = 0.7f)
            f(1f, alpha = 1f, rot = 0f, sx = 1f, sy = 1f)
        },
        kf("spiral-in") {
            f(0f, alpha = 0f, rot = -120f, sx = 0.4f, sy = 0.4f)
            f(1f, alpha = 1f, rot = 0f, sx = 1f, sy = 1f)
        },
        kf("flip-in-x") {
            f(0f, alpha = 0f, rx = -90f)
            f(1f, alpha = 1f, rx = 0f)
        },
        kf("flip-in-y") {
            f(0f, alpha = 0f, ry = -90f)
            f(1f, alpha = 1f, ry = 0f)
        },
        kf("drop-in") {
            f(0f, alpha = 0f, ty = -40f, rot = -6f)
            f(1f, alpha = 1f, ty = 0f, rot = 0f)
        },
        // ── 循环类 ──
        kf("spin") { f(0f, rot = 0f); f(1f, rot = 360f) },
        kf("spin-reverse") { f(0f, rot = 0f); f(1f, rot = -360f) },
        kf("pulse") { f(0f, sx = 1f, sy = 1f); f(0.5f, sx = 1.12f, sy = 1.12f); f(1f, sx = 1f, sy = 1f) },
        kf("bounce") { f(0f, ty = 0f); f(0.5f, ty = -14f); f(1f, ty = 0f) },
        kf("shake") {
            f(0f, tx = 0f)
            f(0.25f, tx = -6f)
            f(0.75f, tx = 6f)
            f(1f, tx = 0f)
        },
    ).associateBy { it.name }

    fun find(name: String): CssKeyframes? = presets[name.trim().lowercase()]

    fun names(): List<String> = presets.keys.sorted()

    fun all(): Map<String, CssKeyframes> = presets

    // ── 构建 DSL ──

    private fun kf(name: String, build: KeyframeBuilder.() -> Unit): CssKeyframes =
        CssKeyframes(name, KeyframeBuilder().apply(build).frames)

    private class KeyframeBuilder {
        val frames = mutableListOf<CssKeyframe>()

        fun f(
            offset: Float,
            alpha: Float? = null,
            tx: Float? = null,
            ty: Float? = null,
            sx: Float? = null,
            sy: Float? = null,
            rot: Float? = null,
            rx: Float? = null,
            ry: Float? = null,
        ) {
            frames.add(CssKeyframe(offset, alpha, tx, ty, sx, sy, rot, rx, ry))
        }
    }
}

/** 动画播放方向 */
enum class CssAnimationDirection { NORMAL, REVERSE, ALTERNATE, ALTERNATE_REVERSE }

/**
 * 一条已解析的动画指令。
 *
 * @param keyframes  关键帧（内置或用户 @keyframes）
 * @param durationMs 单次时长
 * @param delayMs    延迟（stagger 会在渲染时叠加到此处）
 * @param easing     Compose 缓动曲线
 * @param iterations 播放次数，[Int.MAX_VALUE] = 无限循环
 * @param direction  播放方向
 */
data class CssAnimationSpec(
    val keyframes: CssKeyframes,
    val durationMs: Int = 400,
    val delayMs: Int = 0,
    val easing: androidx.compose.animation.core.Easing =
        androidx.compose.animation.core.FastOutSlowInEasing,
    val iterations: Int = 1,
    val direction: CssAnimationDirection = CssAnimationDirection.NORMAL,
) {
    val isInfinite: Boolean get() = iterations == Int.MAX_VALUE
}

/**
 * CSS 动画简写解析。
 *
 * 语法（顺序自由，token 按形态识别）：
 * ```
 * animation: <name> <duration> <easing> <delay> <iteration-count> <direction>
 * enter:     fade-up 300ms ease-out            /* 入场一次 */
 * animation: spin 3s linear infinite
 * ```
 * - 时间：第一个 `Xs`/`Xms` 为时长，第二个为延迟
 * - 缓动：linear / ease / ease-in / ease-out / ease-in-out
 * - 次数：`infinite` 或整数
 * - 方向：normal / reverse / alternate / alternate-reverse
 * - 其余 token：动画名（先查用户 @keyframes，再查内置预设）
 *
 * @param userKeyframes 用户 `@keyframes` 定义（优先于内置同名预设）
 * @return 解析成功返回指令；动画名未知或语法为空返回 null
 */
fun parseCssAnimationSpec(
    value: String?,
    userKeyframes: Map<String, CssKeyframes> = emptyMap(),
    /**
     * 未显式指定次数时，是否允许「视觉常在型」预设默认无限循环。
     * `animation`（常驻动效）传 true；`enter`（入场）必须传 false ——
     * 否则写 `enter: spin` 会变成永久旋转，动画永不结束。
     */
    allowDefaultInfinite: Boolean = true,
): CssAnimationSpec? {
    if (value.isNullOrBlank()) return null
    val tokens = value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null

    var name: String? = null
    var durationMs: Int? = null
    var delayMs = 0
    var easing: androidx.compose.animation.core.Easing =
        androidx.compose.animation.core.FastOutSlowInEasing
    var iterations = 1
    var direction = CssAnimationDirection.NORMAL

    for (token in tokens) {
        val lower = token.lowercase()
        when {
            // 时间：第一个 → 时长，第二个 → 延迟（CSS 简写语义）
            isTimeToken(lower) -> {
                val ms = parseTimeMs(lower)
                if (durationMs == null) durationMs = ms else delayMs = ms
            }
            lower == "infinite" -> iterations = Int.MAX_VALUE
            lower == "linear" -> easing = androidx.compose.animation.core.LinearEasing
            lower == "ease" -> easing = androidx.compose.animation.core.FastOutSlowInEasing
            // 注意：三者曲线不同，此前实现把它们映射到同一条曲线（已修）
            lower == "ease-in" -> easing = androidx.compose.animation.core.FastOutLinearInEasing
            lower == "ease-out" -> easing = androidx.compose.animation.core.LinearOutSlowInEasing
            lower == "ease-in-out" -> easing = androidx.compose.animation.core.FastOutSlowInEasing
            lower == "normal" -> direction = CssAnimationDirection.NORMAL
            lower == "reverse" -> direction = CssAnimationDirection.REVERSE
            lower == "alternate" -> direction = CssAnimationDirection.ALTERNATE
            lower == "alternate-reverse" -> direction = CssAnimationDirection.ALTERNATE_REVERSE
            lower.toIntOrNull() != null -> iterations = lower.toIntOrNull() ?: 1
            else -> if (name == null) name = token
        }
    }

    val frames = name?.let { userKeyframes[it] ?: BuiltinKeyframes.find(it) } ?: return null
    if (frames.isEmpty) return null

    // spin 等"旋转类"预设未显式写次数时默认无限（仅对常驻动效生效；入场动画永远有限次）
    val resolvedIterations = if (
        allowDefaultInfinite && iterations == 1 && durationMs == null && name in defaultInfinite
    ) {
        Int.MAX_VALUE
    } else {
        iterations
    }

    return CssAnimationSpec(
        keyframes = frames,
        durationMs = (durationMs ?: 400).coerceIn(16, 60_000),
        delayMs = delayMs.coerceIn(0, 60_000),
        easing = easing,
        iterations = resolvedIterations,
        direction = direction,
    )
}

/** 未指定时长与次数时默认无限循环的预设（视觉上本就是"常在"的动效） */
private val defaultInfinite = setOf("spin", "pulse", "bounce", "shake")

private fun isTimeToken(token: String): Boolean =
    (token.endsWith("ms") || token.endsWith("s")) &&
        token.removeSuffix("ms").removeSuffix("s").toFloatOrNull() != null

private fun parseTimeMs(token: String): Int = when {
    token.endsWith("ms") -> token.removeSuffix("ms").toFloatOrNull()?.toInt() ?: 0
    token.endsWith("s") -> (token.removeSuffix("s").toFloatOrNull()?.times(1000f))?.toInt() ?: 0
    else -> 0
}

/**
 * 解析 `@keyframes` 块。支持一次解析多个块。
 *
 * @return 动画名 → 关键帧；语法错误 / 无有效帧的块被跳过（不抛异常）
 */
fun parseKeyframesBlocks(css: String): Map<String, CssKeyframes> {
    val result = linkedMapOf<String, CssKeyframes>()
    val header = Regex("""@keyframes\s+([\w-]+)\s*\{""", RegexOption.IGNORE_CASE)
    var searchFrom = 0
    while (true) {
        val match = header.find(css, searchFrom) ?: break
        val bodyStart = match.range.last + 1
        val bodyEnd = findMatchingBrace(css, match.range.last)
        if (bodyEnd < 0) break
        val name = match.groupValues[1]
        val frames = parseFrameList(css.substring(bodyStart, bodyEnd))
        if (frames.isNotEmpty()) result[name] = CssKeyframes(name, frames)
        searchFrom = bodyEnd + 1
    }
    return result
}

/** 从 `{` 的位置开始，找到与之配对的 `}` 下标；未配对返回 -1（支持嵌套块） */
internal fun findMatchingBrace(text: String, openIndex: Int): Int {
    var depth = 0
    var i = openIndex
    while (i < text.length) {
        when (text[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return i
            }
        }
        i++
    }
    return -1
}

/** 解析关键帧列表体：`from { ... } 50% { ... }` */
private fun parseFrameList(body: String): List<CssKeyframe> {
    val frames = mutableListOf<CssKeyframe>()
    val frameRegex = Regex("""([^{}]+)\{([^{}]*)\}""")
    for (match in frameRegex.findAll(body)) {
        val selectors = match.groupValues[1].trim()
        val props = match.groupValues[2]
        if (selectors.isEmpty() || selectors.startsWith("@")) continue
        val offsets = parseOffsets(selectors) ?: continue
        val frame = parseFrameProps(props) ?: continue
        offsets.forEach { offset -> frames.add(frame.copy(offset = offset)) }
    }
    return frames.sortedBy { it.offset }
}

/** 帧选择器 → 归一化 offset 列表：`from`=0 / `to`=1 / `50%`=0.5，支持逗号列表 */
private fun parseOffsets(selectors: String): List<Float>? {
    val parts = selectors.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val offsets = parts.mapNotNull { part ->
        when {
            part == "from" -> 0f
            part == "to" -> 1f
            part.endsWith("%") -> part.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
            else -> part.toFloatOrNull()
        }
    }.map { it.coerceIn(0f, 1f) }
    return offsets.ifEmpty { null }
}

/** 解析单个帧块的属性；无任何可识别属性时返回 null */
private fun parseFrameProps(props: String): CssKeyframe? {
    var opacity: Float? = null
    var translateX: Float? = null
    var translateY: Float? = null
    var scaleX: Float? = null
    var scaleY: Float? = null
    var rotate: Float? = null
    var rotateX: Float? = null
    var rotateY: Float? = null

    for (raw in props.split(';')) {
        val part = raw.trim()
        if (part.isEmpty()) continue
        val idx = part.indexOf(':')
        if (idx <= 0) continue
        val key = part.substring(0, idx).trim().lowercase()
        val value = part.substring(idx + 1).trim()
        if (value.isEmpty()) continue

        when (key) {
            "opacity" -> value.toFloatOrNull()?.let { opacity = it.coerceIn(0f, 1f) }
            "translate-x" -> parseCssPxFloat(value)?.let { translateX = it }
            "translate-y" -> parseCssPxFloat(value)?.let { translateY = it }
            "translate" -> {
                val nums = value.split(Regex("\\s+")).mapNotNull { parseCssPxFloat(it) }
                when (nums.size) {
                    1 -> translateX = nums[0]
                    2 -> { translateX = nums[0]; translateY = nums[1] }
                }
            }
            "scale" -> {
                val nums = value.split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                when (nums.size) {
                    1 -> { scaleX = nums[0]; scaleY = nums[0] }
                    2 -> { scaleX = nums[0]; scaleY = nums[1] }
                }
            }
            "scale-x" -> value.toFloatOrNull()?.let { scaleX = it }
            "scale-y" -> value.toFloatOrNull()?.let { scaleY = it }
            "rotate" -> parseCssAngle(value)?.let { rotate = it }
            "rotate-x", "rotatex", "rotation-x" -> parseCssAngle(value)?.let { rotateX = it }
            "rotate-y", "rotatey", "rotation-y" -> parseCssAngle(value)?.let { rotateY = it }
        }
    }

    val hasAny = listOf(opacity, translateX, translateY, scaleX, scaleY, rotate, rotateX, rotateY)
        .any { it != null }
    if (!hasAny) return null
    return CssKeyframe(
        offset = 0f,
        opacity = opacity,
        translateX = translateX,
        translateY = translateY,
        scaleX = scaleX,
        scaleY = scaleY,
        rotate = rotate,
        rotateX = rotateX,
        rotateY = rotateY,
    )
}

/** 解析角度：`90deg` / `90` → 90f；无法解析 → null */
internal fun parseCssAngle(value: String): Float? {
    val trimmed = value.trim().lowercase()
    return when {
        trimmed.endsWith("deg") -> trimmed.removeSuffix("deg").trim().toFloatOrNull()
        trimmed.endsWith("turn") -> trimmed.removeSuffix("turn").trim().toFloatOrNull()?.times(360f)
        trimmed.endsWith("rad") -> trimmed.removeSuffix("rad").trim().toFloatOrNull()
            ?.times(180f / Math.PI.toFloat())
        else -> trimmed.toFloatOrNull()
    }
}
