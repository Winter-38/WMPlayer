package com.winter.muplayer.ui.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

/**
 * 列表滚动惯性 —— 条目在滚动中按「与视口中心的距离」错开位移，停止时弹回原位。
 *
 * ## 效果
 * 向上/向下滑动时，离视口中心越远的条目位移越大，于是整列条目被"拉开"成不同间隔
 * （视觉上是列表被惯性拉伸/压缩）；手指离开且滚动停止后，速度平滑衰减到 0，
 * 条目各自回弹归位。条目自身的布局占位不变（位移只作用于绘制层），
 * 因此不会触发重新测量，也不会推动 LazyColumn 的滚动位置。
 *
 * ## 读取时机（性能关键）
 * [ListInertiaState.translationFor] 必须在 `Modifier.graphicsLayer { }` 的 **lambda 内部**调用：
 * 该 lambda 在绘制阶段执行，读取其中的状态（速度、layoutInfo）只触发重绘，
 * 不触发重组，也不产生每帧分配。
 *
 * ## 配置
 * 由 playlist 组件 CSS 属性 `item-inertia` 开启（0 = 关闭，>0 = 强度系数，1 为标称幅度）。
 */
class ListInertiaState internal constructor(
    private val listState: LazyListState,
    private val velocity: Animatable<Float, AnimationVector1D>,
) {
    /**
     * 计算某条目当前的绘制层纵向位移（单位：dp，由调用方乘 density）。
     *
     * 位移 = 归一化「条目中心到视口中心的距离」× 归一化滚动速度 × [strength] × [maxOffsetDp]。
     * 条目不在可视范围内、或速度为 0 时返回 0。
     *
     * @param index 条目在列表中的下标
     * @param strength 惯性强度系数（来自 CSS `item-inertia`）
     * @param maxOffsetDp 单条目最大位移（dp），防止极端速度下位移过大
     */
    fun translationFor(index: Int, strength: Float, maxOffsetDp: Float = 18f): Float {
        val v = velocity.value
        if (v == 0f || strength <= 0f) return 0f

        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
        val viewportSize = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        if (viewportSize <= 0f) return 0f

        val centerY = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val itemCenterY = item.offset + item.size / 2f
        // 归一化到 [-1, 1]：-1 = 视口顶部，+1 = 视口底部
        val normalizedDistance = ((itemCenterY - centerY) / (viewportSize / 2f)).coerceIn(-1.6f, 1.6f)
        // 速度归一化：±6000 px/s 视为满强度
        val normalizedVelocity = (v / 6000f).coerceIn(-1f, 1f)

        return (normalizedDistance * normalizedVelocity * strength * maxOffsetDp)
            .coerceIn(-maxOffsetDp, maxOffsetDp)
    }

    /** 当前是否处于惯性激活状态（速度非 0），供调试/降级判断 */
    val isActive: Boolean get() = velocity.value != 0f
}

/**
 * 记住列表惯性状态。
 *
 * @param listState 目标列表的滚动状态（列表与惯性必须绑定同一实例，滚动数据实时同步）
 * @param enabled 是否启用（来自 CSS `item-inertia` > 0）；关闭时不启动任何协程
 * @return 启用时返回状态对象，否则返回 null（调用方可直接跳过 graphicsLayer）
 */
@Composable
fun rememberListInertiaState(
    listState: LazyListState,
    enabled: Boolean,
): ListInertiaState? {
    val velocity = remember { Animatable(0f) }
    val state = remember(listState) { ListInertiaState(listState, velocity) }

    LaunchedEffect(listState, enabled) {
        if (!enabled) {
            velocity.snapTo(0f)
            return@LaunchedEffect
        }

        // ① 采样滚动速度：位置差分 + 指数平滑（避免单帧抖动导致条目跳动）
        launch {
            var lastPos = Float.NaN
            var lastNanos = 0L
            snapshotFlow {
                listState.firstVisibleItemIndex * 100_000f + listState.firstVisibleItemScrollOffset
            }.collect { pos ->
                val now = System.nanoTime()
                if (lastNanos != 0L) {
                    val dtSec = (now - lastNanos) / 1_000_000_000f
                    if (dtSec > 0.0005f) {
                        val raw = (pos - lastPos) / dtSec
                        val smoothed = velocity.value * 0.65f + raw * 0.35f
                        velocity.snapTo(smoothed.coerceIn(-12_000f, 12_000f))
                    }
                }
                lastPos = pos
                lastNanos = now
            }
        }

        // ② 停止滚动 → 速度弹性归零（条目回位；轻微回弹更接近物理惯性手感）
        launch {
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                if (!scrolling && velocity.value != 0f) {
                    velocity.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                }
            }
        }
    }

    return if (enabled) state else null
}
