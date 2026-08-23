package com.winter.muplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 点击粒子爆发特效的触发状态。
 * 每次调用 [burst] 都会在按钮中心触发一组向四周辐射散开的粒子。
 */
@Stable
class ParticleBurstState internal constructor() {
    internal var trigger by mutableIntStateOf(0)
        private set

    /** 触发一次粒子爆发 */
    fun burst() {
        trigger++
    }
}

@Composable
fun rememberParticleBurstState(): ParticleBurstState = remember { ParticleBurstState() }

/**
 * 为按钮附加粒子特效的 modifier 方案（不改变布局），
 * 适用于无法用 [ParticleBurstBox] 包裹的场景（例如 TabRow 内的 [androidx.compose.material3.Tab] / FilterChip，
 * 它们需要自己的 scope 或会破坏父级布局）。
 *
 * 用法：
 * ```
 * val (burst, burstModifier) = rememberParticleBurstEffect(color = MaterialTheme.colorScheme.primary)
 * Button(
 *     onClick = { burst.burst(); doSomething() },
 *     modifier = Modifier.then(burstModifier),
 * ) { ... }
 * ```
 *
 * @return 第一个元素是触发状态（在 onClick 中调用 [ParticleBurstState.burst]），
 *         第二个元素是需叠加到按钮 modifier 链上的坐标追踪 modifier。
 */
@Composable
fun rememberParticleBurstEffect(
    color: Color,
    radius: Dp = 64.dp,
    durationMillis: Int = 480,
): Pair<ParticleBurstState, Modifier> {
    val host = LocalParticleBurstHost.current
    val enabled = LocalParticleBurstEnabled.current
    val density = LocalDensity.current
    val state = rememberParticleBurstState()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(state.trigger) {
        if (state.trigger == 0 || !enabled || host == null) return@LaunchedEffect
        val coords = coordinates ?: return@LaunchedEffect
        host.addBurst(
            color = color,
            center = coords.localToRoot(Offset(coords.size.width / 2f, coords.size.height / 2f)),
            radiusPx = with(density) { radius.toPx() },
            durationMillis = durationMillis,
        )
    }
    return state to Modifier.onGloballyPositioned { coordinates = it }
}

/** 单个粒子的随机参数（每次触发时重新生成，动画期间固定） */
internal class BurstParticle(
    val angle: Float,     // 发射方向角（弧度）
    val distance: Float,  // 相对扩散距离 0..1（乘以半径）
    val size: Float,      // 粒子直径（px）
    val delayF: Float,    // 延迟占比 0..1（相对总时长）
)

/** 一次正在进行的粒子爆发（由 ParticleBurstHost 统一驱动和绘制） */
@Stable
internal class ActiveBurst(
    val color: Color,
    val center: Offset,        // 全局（root）坐标系下的爆发中心
    val radiusPx: Float,       // 扩散半径（px）
    val particles: List<BurstParticle>,
) {
    val progress = Animatable(1f)
}

/**
 * 全局粒子爆发宿主状态 —— 收集所有按钮触发的爆发，统一在最上层绘制。
 * 一个 app 只有一个实例（由 ParticleBurstHost 持有），
 * 粒子因此能浮现在所有 UI（含其他按钮）之上。
 */
@Stable
class ParticleBurstHostState internal constructor(private val scope: CoroutineScope) {
    internal val bursts = mutableStateListOf<ActiveBurst>()

    /**
     * 触发一次粒子爆发。
     * @param center 全局坐标系下的按钮中心
     * @param radiusPx 扩散半径（px）
     */
    fun addBurst(color: Color, center: Offset, radiusPx: Float, durationMillis: Int) {
        val burst = ActiveBurst(color, center, radiusPx, randomParticles(radiusPx))
        bursts.add(burst)
        scope.launch {
            burst.progress.snapTo(0f)
            burst.progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
            )
            bursts.remove(burst)
        }
    }

    private fun randomParticles(radiusPx: Float): List<BurstParticle> {
        val random = Random(System.nanoTime())
        return List(18) {
            BurstParticle(
                angle = random.nextFloat() * (2f * PI.toFloat()),
                distance = 0.5f + random.nextFloat() * 0.5f,        // 0.5~1.0
                size = radiusPx * (0.04f + random.nextFloat() * 0.04f), // 直径约为半径的 4%~8%
                delayF = random.nextFloat() * 0.18f,
            )
        }
    }
}

/** 当前全局粒子宿主；未挂载 ParticleBurstHost 时为 null（粒子静默降级） */
val LocalParticleBurstHost = compositionLocalOf<ParticleBurstHostState?> { null }

/** 粒子特效总开关（来自设置），关闭时按钮不再触发粒子 */
val LocalParticleBurstEnabled = staticCompositionLocalOf { true }

/**
 * 全局粒子爆发宿主：挂载在 app 内容的最上层（例如根部 Box 的最后一个子节点），
 * 负责绘制所有按钮触发的粒子。
 *
 * 注意：hostState 必须在 app 根部创建并通过 CompositionLocalProvider 提供给所有
 * 按钮（ParticleBurstBox），否则按钮读不到宿主、粒子不会发射。
 *
 * 特性：
 * - 粒子绘制在所有 UI 之上，飞出按钮边界后可以遮挡其他按钮（仅视觉）
 * - 本层只绘制、不参与命中测试：粒子不会拦截任何点击
 * - 通过 [LocalParticleBurstEnabled] 控制总开关（设置页可关闭）
 */
@Composable
fun ParticleBurstHost(
    hostState: ParticleBurstHostState,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        hostState.bursts.forEach { b ->
            val t = b.progress.value
            if (t <= 0f || t >= 1f) return@forEach
            b.particles.forEach { p ->
                // 每粒子独立延迟：延迟前不出现，之后在剩余时间内完成扩散
                val local = ((t - p.delayF) / (1f - p.delayF)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val eased = 1f - (1f - local) * (1f - local) // easeOutQuad
                val dist = b.radiusPx * p.distance * eased
                val alpha = (1f - local) * 0.9f
                val r = p.size / 2f * (1f - local * 0.35f)
                drawCircle(
                    color = b.color.copy(alpha = alpha),
                    radius = r,
                    center = b.center + Offset(cos(p.angle) * dist, sin(p.angle) * dist),
                )
            }
        }
    }
}

/**
 * 按钮粒子爆发包装盒：把 content（通常是 IconButton 等可交互控件）包在 Box 中，
 * 点击时调用 [state.burst]()，粒子由全局 ParticleBurstHost 在最上层统一绘制。
 *
 * - 粒子颜色取按钮自身颜色（图标/前景色），由调用方通过 [color] 传入
 * - 扩散半径默认 64dp（mdpi 下 1dp = 1px，即约 64px），可通过 [radius] 调整
 * - 总开关关闭（LocalParticleBurstEnabled = false）或未挂载 Host 时，点击不产生粒子
 */
@Composable
fun ParticleBurstBox(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    state: ParticleBurstState = rememberParticleBurstState(),
    radius: Dp = 64.dp,
    content: @Composable () -> Unit,
) {
    val host = LocalParticleBurstHost.current
    val enabled = LocalParticleBurstEnabled.current
    val density = LocalDensity.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates = it },
    ) {
        content()
    }

    // 监听触发计数变化：每次 burst() 后向全局宿主发射一次粒子
    LaunchedEffect(state.trigger) {
        if (state.trigger == 0) return@LaunchedEffect
        if (!enabled || host == null) return@LaunchedEffect
        val coords = coordinates ?: return@LaunchedEffect
        val center = coords.localToRoot(
            Offset(coords.size.width / 2f, coords.size.height / 2f)
        )
        host.addBurst(
            color = color,
            center = center,
            radiusPx = with(density) { radius.toPx() },
            durationMillis = 480,
        )
    }
}
