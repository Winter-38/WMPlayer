package com.winter.muplayer.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 液态玻璃背景协调状态 —— 基于 **AndroidLiquidGlass**（github.com/Kyant0/AndroidLiquidGlass，
 * 本仓库 `:backdrop` 模块移植其 backdrop 库）：
 *
 * - 被覆盖的内容（列表层）通过 [Modifier.captureLiquidGlassContent] 应用 `layerBackdrop`
 *   把自身**单次录制**到 [GraphicsLayer]（绘制级快照，不重新组合 UI）；
 * - mini 播放器等浮层通过 `Modifier.drawBackdrop(backdrop = 该 LayerBackdrop, ...)` 在绘制时
 *   把内容层以**正确屏幕位置**绘制到自身后方，再叠加 blur / lens（AGSL 折射 + 色散）/
 *   highlight（SDF 边缘高光）/ shadow / innerShadow 等效果 —— 与 Kyant 示例组件
 *   （LiquidBottomTabs / LiquidButton）同模式。
 *
 * 坐标换算：`LayerBackdrop.drawBackdrop` 内部通过 `layerCoordinates.localPositionOf(目标坐标)`
 * 自动对齐，内容与浮层只需处于同一渲染树（同 Window），无需手动上报区域。
 *
 * 触发方式：`active` 由 mini 播放器（CSS `render-style`）决定，激活时内容层才录制。
 */
@Stable
class LiquidGlassBackdrop internal constructor(
    val layerBackdrop: LayerBackdrop
) {

    /** 液态玻璃是否激活（mini 播放器 CSS render-style: blur）；激活时内容层才录制。 */
    var active: Boolean by mutableStateOf(false)

    companion object {
        // ── 液态玻璃默认视觉参数（CSS #playbar liquid-* / blur-radius 缺失 / 解析失败时兜底）──
        // 与原版 AndroidLiquidGlass 示例同源（LiquidBottomTabs: lens(24dp, 24dp)；
        // LiquidButton: lens(12dp, 24dp)）：edge/refraction 均为 dp 语义，
        // 运行期在渲染器处经 density 转为物理像素。
        /** 模糊半径（dp 语义，映射 backdrop blur 半径；毛玻璃/液态玻璃共用） */
        const val DEFAULT_BLUR_RADIUS_DP = 12f
        /** 边缘隆起过渡宽度（折射带，dp 语义，映射 backdrop lens refractionHeight） */
        const val DEFAULT_EDGE_WIDTH_DP = 28f
        /** 边缘折射偏移强度（dp 语义，映射 backdrop lens refractionAmount） */
        const val DEFAULT_REFRACTION_DP = 36f
        /** 表面基色不透明度（0..1，映射 onDrawSurface 基色 alpha；越小越透明） */
        const val DEFAULT_SURFACE_ALPHA = 0.15f
        /** 镜面高光强度（映射 highlight alpha） */
        const val DEFAULT_SPECULAR = 0.45f
        /** 高光锐度（映射 highlight falloff，越大越锐利） */
        const val DEFAULT_SHININESS = 48f
        /** rim 边缘亮线强度（映射 rim 白线 alpha；示例默认无 rim，需时滑块/ CSS 调回） */
        const val DEFAULT_RIM_STRENGTH = 0.3f
    }
}

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdrop {
    val layerBackdrop = rememberLayerBackdrop()
    return remember(layerBackdrop) { LiquidGlassBackdrop(layerBackdrop) }
}

/** 当前液态玻璃背景状态；overlay 布局（叠放）渲染时提供，content 与浮层共享。 */
val LocalGlassBackdrop = staticCompositionLocalOf<LiquidGlassBackdrop?> { null }

/**
 * 应用到**被覆盖的内容**（如列表层）的 capture modifier：
 * 液态玻璃激活时把内容层录制到 backdrop 的 GraphicsLayer（浮层绘制时引用）；
 * 未激活时返回原 modifier（零开销）。
 */
fun Modifier.captureLiquidGlassContent(backdrop: LiquidGlassBackdrop): Modifier =
    if (backdrop.active) then(Modifier.layerBackdrop(backdrop.layerBackdrop)) else this
