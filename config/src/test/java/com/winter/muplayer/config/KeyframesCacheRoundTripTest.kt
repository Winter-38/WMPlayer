package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 配置编译缓存的往返完整性 —— 保护 v3 格式：
 * keyframes 的帧新增 `rotateX` / `rotateY`（3D 翻转入场）后，
 * 写入 → 读回必须逐字段一致；任何字段漏写都会让「冷启动命中缓存」的动画静默变形。
 */
class KeyframesCacheRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun roundTrip(css: CssRuleTable): CssRuleTable {
        val dir = tmp.newFolder()
        BinaryCache.write(dir, ComponentLayout(), css)
        val read = BinaryCache.tryRead(dir)
        assertNotNull("缓存读取失败", read)
        return read!!.second
    }

    @Test
    fun 关键帧全部通道往返一致() {
        val keyframes = mapOf(
            "flip-in-y" to CssKeyframes(
                "flip-in-y",
                listOf(
                    CssKeyframe(0f, opacity = 0f, translateX = -8f, translateY = 12f, scaleX = 0.8f, scaleY = 0.9f, rotate = -30f, rotateX = -12f, rotateY = -90f),
                    CssKeyframe(1f, opacity = 1f, translateX = 0f, translateY = 0f, scaleX = 1f, scaleY = 1f, rotate = 0f, rotateX = 0f, rotateY = 0f),
                ),
            ),
        )
        val css = CssRuleTable(
            rules = mapOf("#fp-cover" to mapOf("enter" to "flip-in-y 380ms ease-out")),
            keyframes = keyframes,
        )

        val restored = roundTrip(css)
        val frames = restored.keyframes["flip-in-y"]?.frames
        assertNotNull("keyframes 丢失", frames)
        assertEquals(2, frames!!.size)

        val first = frames[0]
        assertEquals(0f, first.offset, 0.001f)
        assertEquals(0f, first.opacity ?: -1f, 0.001f)
        assertEquals(-8f, first.translateX ?: 0f, 0.001f)
        assertEquals(12f, first.translateY ?: 0f, 0.001f)
        assertEquals(0.8f, first.scaleX ?: 0f, 0.001f)
        assertEquals(0.9f, first.scaleY ?: 0f, 0.001f)
        assertEquals(-30f, first.rotate ?: 0f, 0.001f)
        assertEquals(-12f, first.rotateX ?: 0f, 0.001f)
        assertEquals(-90f, first.rotateY ?: 0f, 0.001f)

        val second = frames[1]
        assertEquals(1f, second.offset, 0.001f)
        assertEquals(1f, second.opacity ?: -1f, 0.001f)
        assertEquals(0f, second.rotateY ?: -1f, 0.001f)
    }

    @Test
    fun 未声明的通道往返后仍为空() {
        val keyframes = mapOf(
            "fade-only" to CssKeyframes(
                "fade-only",
                listOf(CssKeyframe(0f, opacity = 0f), CssKeyframe(1f, opacity = 1f)),
            ),
        )
        val restored = roundTrip(CssRuleTable(keyframes = keyframes))
        val frames = restored.keyframes["fade-only"]?.frames.orEmpty()
        assertEquals(2, frames.size)
        // 未参与动画的通道必须保持 null，否则会被当成显式值参与插值（旋转/缩放会卡在默认值上）
        assertEquals(null, frames[0].rotateX)
        assertEquals(null, frames[0].rotateY)
        assertEquals(null, frames[0].scaleX)
        assertEquals(null, frames[0].translateX)
    }

    @Test
    fun 规则表与自定义组件同时往返() {
        val css = CssRuleTable(rules = mapOf(".app-top" to mapOf("arrange" to "row")))
        val layout = ComponentLayout(
            customComponents = mapOf("my-icon" to mapOf("icon" to "ic_search")),
        )
        val dir = tmp.newFolder()
        BinaryCache.write(dir, layout, css)
        val read = BinaryCache.tryRead(dir)
        assertNotNull(read)
        assertEquals("row", read!!.second.rules[".app-top"]?.get("arrange"))
        assertEquals("ic_search", read.first.customComponents["my-icon"]?.get("icon"))
    }
}
