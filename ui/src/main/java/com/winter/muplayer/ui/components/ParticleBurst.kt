package com.winter.muplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 点击粒子爆发特效的触发状态。
 * 每次调用 [burst] 都会在按钮中心触发一组向四周辐射散开的粒子。
 *
 * 发射是**同步**的：宿主组件（ParticleBurstBox / rememberParticleBurstEffect /
 * rememberFingerBurst）在组合期把发射器注入 [fire]，[burst] 直接调用它。
 * 不依赖 LaunchedEffect + 触发计数消费 —— 计数若在一次点击的重组提交前被下一次
 * 点击覆盖（连续点击 / 主线程繁忙导致重组延迟，如全屏播放器的模糊背景 / 切歌），
 * 那次点击的粒子会丢失，表现为「有概率不播放特效」。
 */
@Stable
class ParticleBurstState internal constructor() {
    /** 宿主注入的同步发射器；每次重组以最新参数重建。主线程读写，无需快照状态。 */
    internal var fire: (() -> Unit)? = null

    /** 触发一次粒子爆发：宿主已注册时立即发射（未注册时静默，正常不会发生） */
    fun burst() {
        fire?.invoke()
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
    val style = LocalParticleBurstStyle.current
    val colorOverride = LocalParticleColorOverride.current
    val density = LocalDensity.current
    val state = rememberParticleBurstState()
    // 坐标槽：普通数组（非快照状态），由 onGloballyPositioned 在布局回调中写入、burst() 同步读取。
    // 若用 by remember 委托状态，闭包会捕获旧重组的委托引用，布局回调更新的值读不到。
    val coordsSlot = remember { arrayOfNulls<LayoutCoordinates>(1) }

    // 固定色配置优先：统一覆盖组件自带颜色，保证各点击区域颜色一致
    val effectColor = colorOverride ?: color
    val radiusPx = with(density) { radius.toPx() }

    // 组合期注入同步发射器（每次重组以最新 host/开关/样式/坐标重建闭包），
    // burst()（onClick 中调用）即时发射，不依赖重组时序，点击必有粒子。
    state.fire = {
        if (enabled && host != null) {
            val coords = coordsSlot[0]
            if (coords != null) {
                host.addBurst(
                    color = effectColor,
                    center = coords.localToRoot(Offset(coords.size.width / 2f, coords.size.height / 2f)),
                    radiusPx = radiusPx,
                    durationMillis = durationMillis,
                    style = style,
                )
            }
        }
    }
    DisposableEffect(state) {
        onDispose { state.fire = null }
    }
    return state to Modifier
        .renderedColor(effectColor)
        .onGloballyPositioned { coordsSlot[0] = it }
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
    val center: Offset,        // 宿主绘制层局部坐标系下的爆发中心（addBurst 时由 root 坐标换算）
    val radiusPx: Float,       // 扩散半径（px）
    val style: ParticleBurstStyle, // 触发时的粒子样式（快照：动画进行中切样式不影响在飞粒子）
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
     * 宿主绘制层（ParticleBurstHost 的 Canvas）坐标；由 ParticleBurstHost 挂载时写入、
     * 卸载时清空。用于把发射方上报的窗口 root 坐标换算为绘制层局部坐标：
     * ParticleLayer（BottomSheet / Dialog 内容）的宿主只覆盖内容区域，Canvas 原点与
     * 窗口 root 原点不重合，若直接用 root 坐标绘制，粒子会偏移到面板/对话框之外而不可见。
     * 主窗口全屏宿主原点即窗口原点，换算恒等，不受影响。
     */
    internal var hostCoordinates: LayoutCoordinates? = null

    /**
     * 触发一次粒子爆发。
     * @param center 窗口 root 坐标系下的按钮中心/按下位置（发射方 localToRoot 结果），
     *               宿主内部自动换算到自身绘制层坐标
     * @param radiusPx 扩散半径（px）
     * @param style 粒子样式（按触发时的选择渲染）
     */
    fun addBurst(
        color: Color,
        center: Offset,
        radiusPx: Float,
        durationMillis: Int,
        style: ParticleBurstStyle,
    ) {
        // root → 宿主绘制层局部坐标：局部 = root - 绘制层在窗口 root 中的左上角偏移。
        // （UI 层无旋转/缩放，平移换算即精确；绘制层未布局/已脱离组合时回退 root 原值）
        val localCenter = hostCoordinates?.let { coords ->
            if (coords.isAttached) center - coords.boundsInRoot().topLeft else center
        } ?: center
        val burst = ActiveBurst(color, localCenter, radiusPx, style, createParticles(style, radiusPx))
        bursts.add(burst)
        scope.launch {
            try {
                burst.progress.snapTo(0f)
                burst.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
                )
            } catch (e: CancellationException) {
                // 宿主释放 / 组合销毁导致的取消：正常中断，重新抛出保持取消语义
                throw e
            } catch (t: Throwable) {
                // 单次粒子动画异常不得取消宿主 scope —— scope 是 rememberCoroutineScope 的共享
                // Job，一旦被取消，之后所有 addBurst 的 launch 都不再执行：burst 会一直以初始
                // progress=1f 停在列表里（绘制循环跳过），表现为「某次点击后粒子全部不再播放」。
                // 异常被吞掉，只丢弃这一颗粒子。
            } finally {
                // 无论正常结束 / 取消 / 异常都清理列表，避免残影与无限堆积
                bursts.remove(burst)
            }
        }
    }

    private fun createParticles(style: ParticleBurstStyle, radiusPx: Float): List<BurstParticle> = when (style) {
        ParticleBurstStyle.DOT -> dotParticles(radiusPx)
        ParticleBurstStyle.TECH_FRAMES -> techFrameParticles(radiusPx)
        ParticleBurstStyle.RIPPLE -> rippleParticles(radiusPx)
    }

    /** 光点爆发：18 颗小圆点向四周辐射（默认样式） */
    private fun dotParticles(radiusPx: Float): List<BurstParticle> {
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

    /** 方框套方框：外正框 / 45° 旋转中框 / 内正框，三层最大边长递减、延迟轻微错开；最外层扩散范围与光点爆发（约 1r）相近 */
    private fun techFrameParticles(radiusPx: Float): List<BurstParticle> {
        val maxSide = radiusPx * 1.6f
        return listOf(
            BurstParticle(angle = 0f, distance = 1f, size = maxSide, delayF = 0f),
            BurstParticle(angle = 0f, distance = 1f, size = maxSide * 0.72f, delayF = 0.05f),
            BurstParticle(angle = 0f, distance = 1f, size = maxSide * 0.44f, delayF = 0.10f),
        )
    }

    /** 圆形涟漪：软填充圆 + 主环 + 两道次级环 + 中央亮点；最外环扩散范围与光点爆发（约 1r）相近 */
    private fun rippleParticles(radiusPx: Float): List<BurstParticle> = listOf(
        BurstParticle(angle = 0f, distance = 0.50f, size = 0f, delayF = 0.00f), // 软填充圆（最大半径 0.5r）
        BurstParticle(angle = 0f, distance = 0.70f, size = 0f, delayF = 0.06f), // 主环
        BurstParticle(angle = 0f, distance = 0.85f, size = 0f, delayF = 0.16f), // 次级环
        BurstParticle(angle = 0f, distance = 1.00f, size = 0f, delayF = 0.28f), // 外层细环
        BurstParticle(angle = 0f, distance = 0f, size = 0.12f, delayF = 0f),    // 中央亮点（size = 半径占比）
    )
}


/** 当前全局粒子宿主；未挂载 ParticleBurstHost 时为 null（粒子静默降级） */
val LocalParticleBurstHost = compositionLocalOf<ParticleBurstHostState?> { null }

/** 粒子特效总开关（来自设置），关闭时按钮不再触发粒子 */
val LocalParticleBurstEnabled = staticCompositionLocalOf { true }

/**
 * 点击粒子样式（来自设置）。
 *
 * 新增样式时：在此追加枚举值（**不要改动已有值的顺序**，序号会持久化到
 * SettingsManager.particleStyle），并补全三处：
 * 1. [ParticleBurstHostState] 的粒子布局工厂（createParticles 分支）；
 * 2. ParticleBurstHost 绘制分派 + 对应的 DrawScope 绘制函数；
 * 3. SettingsScreen 样式选择弹窗中的文案映射。
 */
enum class ParticleBurstStyle {
    /** 光点爆发（默认）：若干小圆点从点击处向四周辐射散开，边扩散边缩小渐隐。 */
    DOT,

    /** 方框套方框：中心十字准星 + 外正框 / 45° 旋转中框 / 内正框同步放大扩散，外框四角带高亮角标。 */
    TECH_FRAMES,

    /** 圆形涟漪：中央光点 + 软填充圆 + 多道粗细渐变的圆环先后向四周扩散。 */
    RIPPLE,
}

/** 由持久化序号解析粒子样式；越界 / 未知值回退默认光点爆发。 */
fun particleStyleFromSettings(ordinal: Int): ParticleBurstStyle =
    ParticleBurstStyle.entries.getOrElse(ordinal) { ParticleBurstStyle.DOT }

/** 当前点击粒子样式（来自设置）；未提供时默认光点爆发 */
val LocalParticleBurstStyle = staticCompositionLocalOf { ParticleBurstStyle.DOT }

/**
 * 点击特效颜色覆盖（来自设置）。null = 跟随点击位置的渲染色 / 组件颜色（自适应）；
 * 非 null 时所有点击特效统一使用该颜色（按钮、列表条目、全局空白区一致）。
 */
val LocalParticleColorOverride = staticCompositionLocalOf<Color?> { null }

// ══════════════════════════════════════════════
// 渲染色注册表 —— 全局点击粒子的自动取色来源
// ══════════════════════════════════════════════

/** 一个已注册的渲染色区域：绑定边界（LayoutCoordinates）与颜色。 */
internal class RenderedColorRegion(
    val id: Int,
    var coords: LayoutCoordinates?,
    var color: Color,
    /** 所属粒子宿主层：主窗口 root host 或 ParticleLayer 的 layerHost。用于分层跳过判定 */
    var layer: Any?,
) {
    /** root 坐标是否落在该区域边界内（未布局/已脱离组合时返回 false） */
    fun contains(rootPos: Offset): Boolean {
        val c = coords ?: return false
        if (!c.isAttached) return false
        return c.boundsInRoot().contains(rootPos)
    }
}

/** 一个活动粒子宿主（ParticleBurstHost 绘制层）的屏幕覆盖矩形。 */
internal class HostLayerRect(
    val id: Int,
    val host: ParticleBurstHostState,
    var bounds: Rect?,
)

/**
 * 渲染色注册表 —— 组件通过 [Modifier.renderedColor] 注册自身渲染色与边界，
 * 全局点击粒子层在点击时命中查色，实现「颜色随点击位置的渲染色自动变化」。
 * 后注册的视为更上层，命中时优先。
 *
 * 分层：每个渲染色区域归属一个粒子宿主层（[RenderedColorRegion.layer]），
 * 只有同层的兜底点击层才应命中它 —— 否则全屏面板等覆盖层打开时，其下方被盖住的
 * 主界面行/按钮渲染色会与面板中部坐标重叠，导致面板空白点击被误判为
 * 「自带特效区」而跳过（特效空洞）。
 */
class RenderedColorRegistry {
    private val regions = mutableListOf<RenderedColorRegion>()
    private val hostLayers = mutableListOf<HostLayerRect>()
    private var nextId = 0

    /** 注册区域，返回可更新的 id；coords 可为 null（随后由 onGloballyPositioned 补齐） */
    fun register(coords: LayoutCoordinates?, color: Color, layer: Any?): Int {
        val id = nextId++
        regions.add(RenderedColorRegion(id, coords, color, layer))
        return id
    }

    /** 更新已注册区域的边界与颜色（组件布局/换色时调用） */
    fun update(id: Int, coords: LayoutCoordinates?, color: Color, layer: Any?) {
        val region = regions.find { it.id == id } ?: return
        region.coords = coords
        region.color = color
        region.layer = layer
    }

    /** 注销区域（组件离开组合时调用） */
    fun unregister(id: Int) {
        regions.removeAll { it.id == id }
    }

    /**
     * 返回包含 rootPos 的最上层（后注册）区域渲染色；未命中返回 null。
     * @param layer null = 不过滤（查询方所在层无其他层可见时使用）；
     *              非 null = 只匹配该粒子宿主层注册的区域（ParticleLayer 兜底用）
     */
    fun colorAt(rootPos: Offset, layer: Any? = null): Color? {
        for (i in regions.indices.reversed()) {
            val r = regions[i]
            if (layer != null && r.layer !== layer) continue
            if (r.contains(rootPos)) return r.color
        }
        return null
    }

    /** 注册一个粒子宿主绘制层的覆盖矩形（ParticleBurstHost 组合时调用），返回可更新的 id */
    fun registerHostLayer(host: ParticleBurstHostState): Int {
        val id = nextId++
        hostLayers.add(HostLayerRect(id, host, null))
        return id
    }

    /** 更新宿主绘制层的覆盖矩形（Canvas 布局时用 boundsInRoot 结果） */
    fun updateHostLayer(id: Int, bounds: Rect) {
        hostLayers.find { it.id == id }?.bounds = bounds
    }

    /** 注销宿主绘制层（Canvas 卸载时调用） */
    fun unregisterHostLayer(id: Int) {
        hostLayers.removeAll { it.id == id }
    }

    /**
     * 返回覆盖 rootPos 的最上层（后注册）粒子宿主绘制层；无覆盖返回 null。
     * 覆盖层（全屏面板 / 弹层内容）自己负责其区域内空白的粒子兜底，
     * 主窗口全局点击层应把整个覆盖区域让给该层，避免误命中下方被盖住的渲染色。
     */
    fun coveringHostAt(rootPos: Offset): ParticleBurstHostState? {
        for (i in hostLayers.indices.reversed()) {
            val b = hostLayers[i].bounds ?: continue
            if (b.contains(rootPos)) return hostLayers[i].host
        }
        return null
    }

    /** 当前注册区域数（诊断用） */
    val size: Int get() = regions.size
}

/** 当前渲染色注册表；未提供时全局点击粒子回退主题色 */
val LocalRenderedColorRegistry = compositionLocalOf<RenderedColorRegistry?> { null }

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
 *
 * 坐标系：发射方上报的是窗口 root 坐标，本层组合时记录自身绘制层坐标并在 addBurst
 * 时换算为局部坐标 —— 因此宿主可以挂在任意尺寸/位置的容器上（全屏 root、BottomSheet
 * 内容、Dialog 内容），粒子始终出现在点击处。
 */
@Composable
fun ParticleBurstHost(
    hostState: ParticleBurstHostState,
    modifier: Modifier = Modifier,
) {
    // 记录绘制层自身坐标供 addBurst 做 root → 局部换算；卸载时清空，避免复用残留旧坐标
    DisposableEffect(hostState) {
        onDispose {
            hostState.hostCoordinates = null
        }
    }
    // 把本绘制层注册为「覆盖层」：主窗口全局点击层据此把整个区域让给本层兜底，
    // 避免误命中本层之下被盖住的主界面渲染色（全屏面板中部点击无特效的根因）
    val registry = LocalRenderedColorRegistry.current
    var hostLayerId by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(hostState, registry) {
        val id = registry?.registerHostLayer(hostState)
        hostLayerId = id
        onDispose { if (id != null) registry?.unregisterHostLayer(id) }
    }
    Canvas(
        modifier.onGloballyPositioned {
            hostState.hostCoordinates = it
            hostLayerId?.let { id -> registry?.updateHostLayer(id, it.boundsInRoot()) }
        },
    ) {
        hostState.bursts.forEach { b ->
            val t = b.progress.value
            if (t <= 0f || t >= 1f) return@forEach
            when (b.style) {
                ParticleBurstStyle.DOT -> drawDotBurst(b, t)
                ParticleBurstStyle.TECH_FRAMES -> drawTechFrameBurst(b, t)
                ParticleBurstStyle.RIPPLE -> drawRippleBurst(b, t)
            }
        }
    }
}

// ══════════════════════════════════════════════
// 样式绘制分派 —— 每种 ParticleBurstStyle 一套 DrawScope 绘制实现
// ══════════════════════════════════════════════

/** easeOutQuad：前期快、末期缓（扩散 / 放大动画常用） */
private fun easeOutQuad(x: Float): Float = 1f - (1f - x) * (1f - x)

/** 单个粒子在爆发进度 t（0..1）下，扣除自身延迟后的推进进度 */
private fun BurstParticle.progressAt(t: Float): Float =
    ((t - delayF) / (1f - delayF)).coerceIn(0f, 1f)

/** 光点爆发：小圆点沿各自方向辐射散开，边扩散边缩小渐隐（默认样式） */
private fun DrawScope.drawDotBurst(b: ActiveBurst, t: Float) {
    b.particles.forEach { p ->
        val local = p.progressAt(t)
        if (local <= 0f) return@forEach
        val eased = easeOutQuad(local)
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

/** 方框套方框（科技感）：中心十字准星 + 外正框 / 45° 旋转中框 / 内正框同步放大扩散，外框四角带高亮角标 */
private fun DrawScope.drawTechFrameBurst(b: ActiveBurst, t: Float) {
    val c = b.center
    // 中心十字准星：先亮后收拢淡出
    val crossLen = b.radiusPx * 0.35f * (1f - t)
    if (crossLen > 0.5f) {
        val crossColor = lerp(b.color, Color.White, 0.45f)
        val crossAlpha = (1f - t) * 0.85f
        drawLine(
            crossColor.copy(alpha = crossAlpha),
            Offset(c.x - crossLen, c.y),
            Offset(c.x + crossLen, c.y),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round,
        )
        drawLine(
            crossColor.copy(alpha = crossAlpha),
            Offset(c.x, c.y - crossLen),
            Offset(c.x, c.y + crossLen),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round,
        )
    }
    b.particles.forEachIndexed { index, p ->
        val local = p.progressAt(t)
        if (local <= 0f) return@forEachIndexed
        val eased = easeOutQuad(local)
        val side = p.size * eased                     // 该层当前边长
        if (side <= 0.5f) return@forEachIndexed
        val alpha = (1f - local) * (0.9f - index * 0.16f)
        val strokeW = (side * 0.016f).coerceAtLeast(1.2f)
        when (index) {
            0 -> {
                // 外层正框
                drawRect(
                    color = b.color.copy(alpha = alpha),
                    topLeft = Offset(c.x - side / 2f, c.y - side / 2f),
                    size = Size(side, side),
                    style = Stroke(width = strokeW),
                )
                // 四角 L 形高亮角标（亮白混合，指向框内）
                val cornerColor = lerp(b.color, Color.White, 0.35f)
                    .copy(alpha = (alpha * 1.25f).coerceAtMost(1f))
                val half = side / 2f
                val len = side * 0.16f
                val w = strokeW * 1.4f
                listOf(1f to 1f, 1f to -1f, -1f to 1f, -1f to -1f).forEach { (sx, sy) ->
                    val cx = c.x + sx * half
                    val cy = c.y + sy * half
                    drawLine(cornerColor, Offset(cx, cy), Offset(cx - sx * len, cy), strokeWidth = w, cap = StrokeCap.Round)
                    drawLine(cornerColor, Offset(cx, cy), Offset(cx, cy - sy * len), strokeWidth = w, cap = StrokeCap.Round)
                }
            }
            1 -> {
                // 中层框：旋转 45°，与外框构成“方框套方框”的菱形嵌套
                rotate(degrees = 45f, pivot = c) {
                    drawRect(
                        color = b.color.copy(alpha = alpha),
                        topLeft = Offset(c.x - side / 2f, c.y - side / 2f),
                        size = Size(side, side),
                        style = Stroke(width = strokeW),
                    )
                }
            }
            else -> {
                // 内层小框（正框）
                drawRect(
                    color = b.color.copy(alpha = alpha),
                    topLeft = Offset(c.x - side / 2f, c.y - side / 2f),
                    size = Size(side, side),
                    style = Stroke(width = strokeW),
                )
            }
        }
    }
}

/** 圆形涟漪（蔚蓝档案式）：软填充圆 + 多道粗细渐变的圆环先后扩散，中央亮点轻微上浮缩灭 */
private fun DrawScope.drawRippleBurst(b: ActiveBurst, t: Float) {
    val c = b.center
    b.particles.forEachIndexed { index, p ->
        val local = p.progressAt(t)
        if (local <= 0f) return@forEachIndexed
        val eased = easeOutQuad(local)
        when (index) {
            0 -> {
                // 软填充圆：快速膨胀并淡出，形成柔和光晕
                val r = b.radiusPx * p.distance * eased
                val fade = 1f - local
                drawCircle(
                    color = b.color.copy(alpha = fade * fade * fade * 0.35f),
                    radius = r,
                    center = c,
                )
            }
            1, 2, 3 -> {
                // 圆环：半径向外扩散，线宽随扩散逐渐收细
                val r = b.radiusPx * p.distance * eased
                val ringAlpha = (1f - local) * when (index) {
                    1 -> 0.95f
                    2 -> 0.8f
                    else -> 0.65f
                }
                val baseWidth = when (index) {
                    1 -> 0.10f
                    2 -> 0.055f
                    else -> 0.032f
                }
                drawCircle(
                    color = b.color.copy(alpha = ringAlpha),
                    radius = r,
                    center = c,
                    style = Stroke(width = b.radiusPx * baseWidth * (1f - local) + 1.5f),
                )
            }
            else -> {
                // 中央亮点：轻微上浮并缩灭
                val r = b.radiusPx * p.size * (1f - local)
                drawCircle(
                    color = lerp(b.color, Color.White, 0.5f).copy(alpha = (1f - local) * 0.95f),
                    radius = r,
                    center = Offset(c.x, c.y - b.radiusPx * 0.16f * local),
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
    val style = LocalParticleBurstStyle.current
    val colorOverride = LocalParticleColorOverride.current
    val density = LocalDensity.current
    // 坐标槽：普通数组（非快照状态），onGloballyPositioned 写入、burst() 同步读取
    val coordsSlot = remember { arrayOfNulls<LayoutCoordinates>(1) }

    // 固定色配置优先：统一覆盖组件自带颜色，保证各点击区域颜色一致
    val effectColor = colorOverride ?: color
    val radiusPx = with(density) { radius.toPx() }

    Box(
        modifier = modifier
            .renderedColor(effectColor)
            .onGloballyPositioned { coordsSlot[0] = it },
    ) {
        content()
    }

    // 同步发射器：content 内 onClick 调用 burst() 时立即向宿主发射粒子。
    // 不用 LaunchedEffect + 触发计数 —— 连续点击或主线程繁忙（重组延迟）时
    // 计数可能在重组前被覆盖导致那次点击的粒子丢失。
    state.fire = {
        if (enabled && host != null) {
            val coords = coordsSlot[0]
            if (coords != null) {
                host.addBurst(
                    color = effectColor,
                    center = coords.localToRoot(
                        Offset(coords.size.width / 2f, coords.size.height / 2f)
                    ),
                    radiusPx = radiusPx,
                    durationMillis = 480,
                    style = style,
                )
            }
        }
    }
    DisposableEffect(state) {
        onDispose { state.fire = null }
    }
}

/**
 * 向渲染色注册表注册当前组件的渲染色与边界。
 *
 * 叠加到任何可见组件（按钮、色块等）的 modifier 链上后，全局点击粒子层
 * 在该区域点击时自动取到该渲染色，实现「颜色随点击位置渲染色变化」。
 * 未挂载 [LocalRenderedColorRegistry] 时静默降级（不注册，不影响布局）。
 */
@Composable
fun Modifier.renderedColor(color: Color): Modifier = composed {
    val registry = LocalRenderedColorRegistry.current
    val layer = LocalParticleBurstHost.current   // 渲染色归属的粒子宿主层（root / ParticleLayer）
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val id = remember { registry?.register(null, color, layer) }
    DisposableEffect(id) {
        onDispose { if (id != null) registry?.unregister(id) }
    }
    SideEffect { if (id != null) registry?.update(id, coords, color, layer) }
    this.onGloballyPositioned { coords = it }
}

/**
 * 全局点击粒子层 —— 观察挂载点内的点击（tap），为没有自带点击特效的区域兜底发射粒子。
 *
 * - 纯观察不消费事件：不拦截任何子组件的点击/滚动
 * - 拖动位移超过 touch slop（滚动/滑动）不触发；长按松手视为点击
 * - 点击位置命中已注册渲染色（按钮 / 列表条目等自带点击特效的区域）时**跳过**，
 *   避免与组件自身特效双重触发；仅未注册的空白区域由本层发射
 * - 粒子颜色：命中 [LocalParticleColorOverride] 固定色时使用固定色；
 *   否则回退主题 primary 色
 * - 遵守 [LocalParticleBurstEnabled] 总开关
 *
 * 挂载方式：根 Box 的 modifier，例如 `Modifier.fillMaxSize().globalTapParticles(host)`。
 */
@Composable
fun Modifier.globalTapParticles(host: ParticleBurstHostState): Modifier = composed {
    val enabled by rememberUpdatedState(LocalParticleBurstEnabled.current)
    val style by rememberUpdatedState(LocalParticleBurstStyle.current)
    val colorOverride by rememberUpdatedState(LocalParticleColorOverride.current)
    val registry = LocalRenderedColorRegistry.current
    val density = LocalDensity.current
    val fallbackColor = MaterialTheme.colorScheme.primary
    val radius = 64.dp
    // 挂载点（主窗口根部 Box）自身坐标，用于把局部按下点转成窗口 root 坐标参与渲染色/覆盖层判定
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    this.onGloballyPositioned { rootCoords = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPosition = down.position
                val up = waitForUpOrCancellation()
                if (up != null && enabled) {
                    // 位移超过 touch slop 视为拖动（滚动/滑动手势），不算点击
                    if ((up.position - downPosition).getDistance() <= viewConfiguration.touchSlop) {
                        val rootPos = rootCoords?.localToRoot(downPosition) ?: downPosition
                        // 覆盖层（全屏面板 / ParticleLayer 宿主绘制层）已盖住该点：整块由该层
                        // 自己兜底（其 autoTap 监听命中本层渲染色才跳过），主窗口层不参与，
                        // 避免误命中本层之下被盖住的主界面渲染色 → 面板中部点击无特效的根因。
                        val covering = registry?.coveringHostAt(rootPos)
                        if (covering != null && covering !== host) return@awaitEachGesture
                        // 主界面可见区域内：已注册渲染色 = 该处组件自带点击特效（按钮 / 列表条目），
                        // 跳过以免双重触发
                        if (registry?.colorAt(rootPos) != null) return@awaitEachGesture
                        val color = colorOverride ?: fallbackColor
                        host.addBurst(
                            color = color,
                            center = rootPos,
                            radiusPx = with(density) { radius.toPx() },
                            durationMillis = 480,
                            style = style,
                        )
                    }
                }
            }
        }
}

/**
 * 为自带点击行为的内容（歌曲行 / 卡片 / 队列项 / 分组头等）提供「点击必有、位置尽量精确」的粒子：
 *
 * - 手势**仅记录**手指按下位置（不直接发射），不受滚动 / 长按等手势竞争的干扰；
 * - 由调用方在 onClick / onLongClick 回调前调用返回的 [ParticleBurstState.burst] 显式触发，
 *   因此即使纯手势观察不可靠也能保证出粒子；
 * - 爆发点取最近一次按下的手指位置，手势未记录到时回退组件中心；
 * - 渲染色照常注册：上层弹层空白兜底层据此跳过，避免与本次触发双重发射。
 *
 * 用法：
 * ```
 * val (burst, fingerMod) = rememberFingerBurst()
 * Card(modifier = Modifier.combinedClickable(
 *     onClick = { burst.burst(); onClick() },
 *     onLongClick = { burst.burst(); onLongClick() },
 * ).then(fingerMod)) { ... }
 * ```
 */
@Composable
fun rememberFingerBurst(
    color: Color = MaterialTheme.colorScheme.primary,
    radius: Dp = 48.dp,
): Pair<ParticleBurstState, Modifier> {
    val host = LocalParticleBurstHost.current
    val enabled = LocalParticleBurstEnabled.current
    val style = LocalParticleBurstStyle.current
    val colorOverride = LocalParticleColorOverride.current
    val density = LocalDensity.current
    val state = rememberParticleBurstState()
    // 坐标槽：普通数组（非快照状态），onGloballyPositioned 写入、burst() 同步读取
    val coordsSlot = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val effectColor = colorOverride ?: color
    val radiusPx = with(density) { radius.toPx() }

    // 手势回调与重组都发生在主线程：普通数组即可跨回调传递最新按点，避免每次按下触发重组
    val lastTap = remember { arrayOfNulls<Offset>(1) }

    // 同步发射器：调用方在 onClick / onLongClick 中调用 burst() 时立即发射 ——
    // 不依赖重组计数，连续点击或主线程繁忙（重组延迟）时也不会丢粒子。
    state.fire = {
        if (enabled && host != null) {
            val coords = coordsSlot[0]
            if (coords != null) {
                val tap = lastTap[0]
                val center = tap?.let(coords::localToRoot)
                    ?: coords.localToRoot(Offset(coords.size.width / 2f, coords.size.height / 2f))
                host.addBurst(
                    color = effectColor,
                    center = center,
                    radiusPx = radiusPx,
                    durationMillis = 480,
                    style = style,
                )
            }
        }
    }
    DisposableEffect(state) {
        onDispose { state.fire = null }
    }
    return state to Modifier
        .renderedColor(effectColor)
        .onGloballyPositioned { coordsSlot[0] = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                // 仅记录最近一次按下位置，供 burst() 触发时精确定位（不在此发射粒子）
                val down = awaitFirstDown(requireUnconsumed = false)
                lastTap[0] = down.position
                waitForUpOrCancellation()
            }
        }
}

/**
 * 为自身已带点击行为的组件（列表行、卡片、分组头等）附加点击粒子特效。
 *
 * 与 [rememberParticleBurstEffect]（以组件中心为爆发点、需在 onClick 中手动调用 burst）不同：
 * 本 modifier **以手指按下位置为爆发点**，直接在 modifier 内观察手势发射，调用方无需改动自身点击回调。
 *
 * - 纯观察不消费事件：原 onClick / onLongClick / 滚动等行为不受影响
 * - 拖动位移超过 touch slop（滚动 / 滑动）不触发；长按松手（未移出）视为点击
 * - 渲染色注册：全局点击层命中该区域时自动跳过，避免与自身特效双重发射
 * - 颜色：命中固定色配置时用固定色，否则用 [color]（默认主题 primary）
 * - 遵守粒子总开关 / 样式配置；未挂载宿主时静默降级
 */
@Composable
fun Modifier.particleAtTap(
    color: Color = MaterialTheme.colorScheme.primary,
    radius: Dp = 48.dp,
    registerColor: Boolean = true,
    skipRegistered: Boolean = false,
): Modifier = composed {
    val host = LocalParticleBurstHost.current
    val registry = LocalRenderedColorRegistry.current
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val effectColor = LocalParticleColorOverride.current ?: color

    val enabled by rememberUpdatedState(LocalParticleBurstEnabled.current)
    val style by rememberUpdatedState(LocalParticleBurstStyle.current)
    val colorOverride by rememberUpdatedState(LocalParticleColorOverride.current)
    val colorState by rememberUpdatedState(color)

    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val coordsState by rememberUpdatedState(coordinates)

    this
        .let { if (registerColor) it.renderedColor(effectColor) else it }
        .onGloballyPositioned { coordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPosition = down.position
                val up = waitForUpOrCancellation()
                if (up != null && enabled && host != null) {
                    // 位移超过 touch slop 视为拖动（滚动 / 滑动手势），不算点击
                    if ((up.position - downPosition).getDistance() <= viewConfiguration.touchSlop) {
                        val coords = coordsState ?: return@awaitEachGesture
                        val rootPos = coords.localToRoot(downPosition)
                        // 命中同层渲染色区域 = 该处组件自带 trigger 粒子（列表行 / 卡片 / 按钮），
                        // 跳过以免与组件自身粒子双重发射（ParticleLayer 空白兜底使用）。
                        // 只匹配本粒子宿主层（host）注册的区域 —— 全屏面板等覆盖层打开时，
                        // 其下方被盖住的主界面渲染色与本层坐标重叠，若不按层过滤会把面板空白
                        // 误判为「自带特效区」而跳过，造成点击空洞。
                        if (skipRegistered && registry?.colorAt(rootPos, host) != null) return@awaitEachGesture
                        host.addBurst(
                            color = colorOverride ?: colorState,
                            center = rootPos,
                            radiusPx = radiusPx,
                            durationMillis = 480,
                            style = style,
                        )
                    }
                }
            }
        }
}

/**
 * 独立窗口弹层 / 全屏浮层的粒子容器 —— 让「不论哪个界面 / 弹层，点击都有粒子特效」。
 *
 * ModalBottomSheet、Dialog、Popup 等内容在独立窗口里组合：主界面根部的粒子宿主
 * 收不到这些窗口的点击，其绘制也会被窗口遮住。把弹层内容（或全屏页内容）放入
 * 本容器后，容器内部会自建一套粒子宿主与绘制层（画在内容上方），容器区域内
 * 任意点击都在**手指按下位置**触发粒子（纯观察，不消费事件，滚动不误触）；
 * 粒子样式 / 总开关 / 颜色仍继承外层 CompositionLocal（设置中的配置自动生效）。
 *
 * 用法：
 * ```
 * ModalBottomSheet(onDismissRequest = { ... }) {
 *     ParticleLayer(modifier = Modifier.fillMaxWidth()) {
 *         Column { ... }        // 弹层原有内容
 *     }
 * }
 * Dialog(onDismissRequest = { ... }) {
 *     ParticleLayer { Card { ... } }   // 弹层原有内容
 * }
 * ```
 */
@Composable
fun ParticleLayer(
    modifier: Modifier = Modifier,
    autoTap: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val layerScope = rememberCoroutineScope()
    val layerHost = remember { ParticleBurstHostState(layerScope) }
    CompositionLocalProvider(LocalParticleBurstHost provides layerHost) {
        Box(
            modifier = modifier
                .then(
                    // autoTap=true：容器内空白点击 → 手指位置粒子；已注册渲染色区域（行/卡片/按钮
                    // 自带 trigger 粒子）跳过避免双发。autoTap=false：仅提供独立宿主与绘制层
                    // （用于已由全局层兜底空白的同窗口全屏面板），不自挂手势监听。
                    if (autoTap) {
                        Modifier.particleAtTap(
                            radius = 64.dp,
                            registerColor = false,
                            skipRegistered = true,
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            content()
            // 绘制层置于内容之上：粒子浮在弹层 / 页面内容上方
            ParticleBurstHost(hostState = layerHost, modifier = Modifier.matchParentSize())
        }
    }
}
