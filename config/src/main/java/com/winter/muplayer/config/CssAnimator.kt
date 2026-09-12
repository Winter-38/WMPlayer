package com.winter.muplayer.config

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 组件 / slot 的动画配置（由 CSS 解析而来）。
 *
 * - [enter]  入场动画 —— 首次组合时播放一次，用于界面切换的视觉过渡
 * - [loop]   持续动画 —— `animation` 属性，可无限循环
 * - [staggerMs] slot 内子组件的入场间隔（依次入场）
 *
 * 二者同时存在时：先播 [enter]，结束后接 [loop]。
 */
data class CssAnimationBinding(
    val enter: CssAnimationSpec? = null,
    val loop: CssAnimationSpec? = null,
    val staggerMs: Int = 0,
) {
    val hasAny: Boolean get() = enter != null || loop != null
}

/**
 * 从 CSS 属性解析动画配置。
 *
 * 支持的属性：
 * - `animation`     持续/循环动画（`<name> <duration> <easing> <delay> <count> <direction>`）
 * - `enter`         入场动画（同语法，播放一次，默认 400ms）
 * - `enter-delay`   入场额外延迟
 * - `stagger`       slot 专用：子组件依次入场的间隔（如 `stagger: 60ms`）
 *
 * @param userKeyframes 用户 `@keyframes` 定义，优先于内置预设
 */
fun parseAnimationBinding(
    props: Map<String, String>,
    userKeyframes: Map<String, CssKeyframes> = emptyMap(),
): CssAnimationBinding? {
    val enterSpec = parseCssAnimationSpec(props["enter"], userKeyframes, allowDefaultInfinite = false)
    val loopSpec = parseCssAnimationSpec(props["animation"], userKeyframes)
    val stagger = props["stagger"]?.let { parseDurationTokenMs(it) } ?: 0
    val enterDelay = props["enter-delay"]?.let { parseDurationTokenMs(it) } ?: 0

    val enter = enterSpec?.let { if (enterDelay > 0) it.copy(delayMs = it.delayMs + enterDelay) else it }
    if (enter == null && loopSpec == null && stagger <= 0) return null
    return CssAnimationBinding(enter = enter, loop = loopSpec, staggerMs = stagger)
}

/** `60ms` / `0.2s` / `120` → 毫秒 */
internal fun parseDurationTokenMs(value: String): Int {
    val trimmed = value.trim().lowercase()
    return when {
        trimmed.endsWith("ms") -> trimmed.removeSuffix("ms").trim().toFloatOrNull()?.toInt() ?: 0
        trimmed.endsWith("s") -> trimmed.removeSuffix("s").trim().toFloatOrNull()?.times(1000f)?.toInt() ?: 0
        else -> trimmed.toFloatOrNull()?.toInt() ?: 0
    }.coerceIn(0, 60_000)
}

/**
 * 动画宿主（Modifier 扩展）—— 把 [binding] 描述的动画应用到绘制层。
 *
 * ## 为什么是 Modifier 而非包一层 Box
 * 包 Box 会改变测量与对齐（内容从原对齐塌到 Box 的 TopStart，或被居中），
 * 而 Modifier 扩展只追加 graphicsLayer，**不引入任何布局层级**，
 * 原有布局结果完全不变 —— slot 与组件因此可以共用同一套动画。
 *
 * ## 为什么用 graphicsLayer
 * 平移 / 缩放 / 旋转 / 透明度全部走 `graphicsLayer{}`（绘制阶段），**不参与测量与布局**：
 * - 组件/slot 的理论存在区域（布局占位）在整个动画过程中保持不变；
 * - 相邻元素不会被推动，不会产生连锁重新布局；
 * - 动画状态在 `graphicsLayer{}` 的 lambda 内读取（延迟读取），因此每帧只触发**重绘**，
 *   不触发重组，也不在每帧分配对象。
 *
 * [staggerIndex] 用于 slot 内子组件依次入场：实际延迟 = `staggerMs * staggerIndex`。
 * [inheritedStaggerMs] 是父 slot 声明的 `stagger`：组件自身未声明时继承之，
 * 用于实现「slot 的动画控制内部组件位置」——slot 不指定 `stagger` 时内部组件互不错开。
 * [replayKey] 变化时重播入场动画（默认 `Unit`，即每个组件实例只播一次）。
 */
@Composable
fun Modifier.cssAnimated(
    binding: CssAnimationBinding?,
    staggerIndex: Int = 0,
    inheritedStaggerMs: Int = 0,
    replayKey: Any? = Unit,
): Modifier {
    if (binding == null || !binding.hasAny) return this

    val enter = binding.enter
    val loop = binding.loop
    // 子组件实际使用的 stagger 间隔：自身声明优先，否则继承父 slot
    val effectiveStaggerMs = if (binding.staggerMs > 0) binding.staggerMs else inheritedStaggerMs
    val progress = remember { Animatable(0f) }

    // key 只用配置本身（内容相等的 data class 不会重启动画），replayKey 显式变化才重播
    LaunchedEffect(enter, loop, staggerIndex, effectiveStaggerMs, replayKey) {
        val staggerDelay = (effectiveStaggerMs.toLong() * staggerIndex)

        if (enter != null) {
            progress.snapTo(0f)
            delay(enter.delayMs + staggerDelay)
            runAnimation(enter, progress)
        }
        if (loop != null) {
            val base = if (enter != null) 0L else loop.delayMs + staggerDelay
            if (base > 0) delay(base)
            runAnimation(loop, progress)
        }
    }

    // 动画帧 → 绘制参数。frames 在此 lambda 内读取 Animatable.value：
    // 状态读取发生在绘制阶段，因此只 invalidate 绘制，不触发重组。
    val frames = (enter ?: loop)!!.keyframes
    return this.graphicsLayer {
        val t = progress.value
        val pxPerDp = density
        alpha = frames.alphaAt(t)
        translationX = frames.translateXAt(t) * pxPerDp
        translationY = frames.translateYAt(t) * pxPerDp
        scaleX = frames.scaleXAt(t)
        scaleY = frames.scaleYAt(t)
        rotationZ = frames.rotationAt(t)
        rotationX = frames.rotationXAt(t)
        rotationY = frames.rotationYAt(t)
        // 3D 翻转的透视深度：默认 8×density 在组件尺寸较大时透视过强（边缘拉伸明显），
        // 调到 16×density 更接近 CSS `perspective: 1000px` 的观感；rotationX/Y 为 0 时无影响。
        cameraDistance = 16f * density
    }
}

/** 单次迭代是否反向播放（对齐 CSS `animation-direction` 语义） */
private fun iterationReversed(index: Int, direction: CssAnimationDirection): Boolean = when (direction) {
    CssAnimationDirection.NORMAL -> false
    CssAnimationDirection.REVERSE -> true
    CssAnimationDirection.ALTERNATE -> index % 2 == 1
    CssAnimationDirection.ALTERNATE_REVERSE -> index % 2 == 0
}

/**
 * 驱动一次完整动画：按 [CssAnimationSpec.iterations] 迭代，无限时持续循环。
 * 首帧 `snapTo` 起始值（保证延迟期间停在起始帧，形成"入场前"状态），
 * 末尾停在终止值（fill: forwards 语义），因此动画结束后不会回弹。
 */
private suspend fun runAnimation(spec: CssAnimationSpec, progress: Animatable<Float, AnimationVector1D>) {
    val duration = spec.durationMs
    var index = 0
    while (true) {
        val reversed = iterationReversed(index, spec.direction)
        val from = if (reversed) 1f else 0f
        val to = if (reversed) 0f else 1f
        progress.snapTo(from)
        progress.animateTo(to, animationSpec = tween(durationMillis = duration, easing = spec.easing))
        index++
        if (!spec.isInfinite && index >= spec.iterations) break
        if (spec.isInfinite && index > 100_000) break // 防御：异常配置下不至于永久占用协程
    }
}
