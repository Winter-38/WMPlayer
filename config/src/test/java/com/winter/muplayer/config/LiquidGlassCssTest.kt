package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 液态玻璃参数设置链路测试：
 * - 默认 styles.css 模板含 #playbar liquid-* 属性（设置页滑块读取/写入的契约值）；
 * - CssParser 支持连字符属性名（liquid-edge 等）；
 * - 运行期参数解析函数 parseCssDp / parseCssNumber 行为正确（缺失/非法 → 兜底默认）。
 */
class LiquidGlassCssTest {

    @Test
    fun `默认CSS中playbar含液态玻璃默认属性`() {
        val rules = CssParser.parse(StyleConfigLoader.defaultStylesCss())
        val playbar = rules["#playbar"] ?: error("默认模板缺少 #playbar 规则")
        // edge/refraction 为 dp 语义（原版示例 LiquidBottomTabs: lens(24dp, 24dp)，refraction 调高为 32dp）
        assertEquals("28dp", playbar["liquid-edge"])
        assertEquals("36dp", playbar["liquid-refraction"])
        // 模糊半径：默认 0（无效果模式）；毛玻璃/液态玻璃由设置页滑块写入
        assertEquals("0", playbar["blur-radius"])
        // 表面基色不透明度（默认 0.1 高透）
        assertEquals("0.15", playbar["liquid-opacity"])
        assertEquals("0.45", playbar["liquid-specular"])
        assertEquals("48", playbar["liquid-shininess"])
        // rim 默认关闭（按示例重做，需时滑块/CSS 调回）
        assertEquals("0.3", playbar["liquid-rim"])
    }

    @Test
    fun cssParser_支持连字符属性名() {
        val rules = CssParser.parse(
            "#playbar { render-style: liquid; blur-radius: 8dp; liquid-edge: 28dp; liquid-rim: 1.2; }"
        )
        val playbar = rules["#playbar"] ?: error("解析失败")
        assertEquals("liquid", playbar["render-style"])
        assertEquals("8dp", playbar["blur-radius"])
        assertEquals("28dp", playbar["liquid-edge"])
        assertEquals("1.2", playbar["liquid-rim"])
    }

    @Test
    fun parseCssDp_支持dp_px_纯数字_非法归零() {
        assertEquals(24f, parseCssDp("24dp").value)
        assertEquals(24f, parseCssDp("24px").value)   // px 按 dp 同值处理（解析语义统一为 dp）
        assertEquals(14f, parseCssDp(" 14dp ").value)
        assertEquals(3.5f, parseCssDp("3.5").value)
        assertEquals(0f, parseCssDp("abc").value)     // 非法 → 0
    }

    @Test
    fun parseCssPxFloat_支持px_dp_纯数字_非法返回null() {
        assertEquals(24f, parseCssPxFloat("24px"))
        assertEquals(14f, parseCssPxFloat(" 14px "))
        assertEquals(10f, parseCssPxFloat("10dp"))
        assertEquals(3.5f, parseCssPxFloat("3.5"))
        assertNull(parseCssPxFloat("abc"))
        assertNull(parseCssPxFloat(""))
        assertNull(parseCssPxFloat(null))
    }

    @Test
    fun parseCssNumber_仅接受无单位数值() {
        assertEquals(0.55f, parseCssNumber("0.55"))
        assertEquals(48f, parseCssNumber("48"))
        assertEquals(1.5f, parseCssNumber(" 1.5 "))
        assertNull(parseCssNumber("24px"))
        assertNull(parseCssNumber("blur"))
        assertNull(parseCssNumber(null))
    }

    @Test
    fun backdrop默认值与CSS默认一致() {
        assertEquals(12f, LiquidGlassBackdrop.DEFAULT_BLUR_RADIUS_DP)
        assertEquals(28f, LiquidGlassBackdrop.DEFAULT_EDGE_WIDTH_DP)
        assertEquals(36f, LiquidGlassBackdrop.DEFAULT_REFRACTION_DP)
        assertEquals(0.15f, LiquidGlassBackdrop.DEFAULT_SURFACE_ALPHA, 0.0001f)
        assertEquals(0.45f, LiquidGlassBackdrop.DEFAULT_SPECULAR, 0.0001f)
        assertEquals(48f, LiquidGlassBackdrop.DEFAULT_SHININESS)
        assertEquals(0.3f, LiquidGlassBackdrop.DEFAULT_RIM_STRENGTH, 0.0001f)
    }
}
