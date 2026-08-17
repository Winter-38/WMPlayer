package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * BinaryCache 全屏播放器序列化测试。
 *
 * 验证含 children 背景层的 fullPlayerSlots 经 BinaryCache 写入/读取后
 * 结构完全一致（覆盖 TYPE_CHILDREN 嵌套序列化路径）。
 */
class FullPlayerSlotsBinaryCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun 含children的fullPlayerSlots_序列化往返一致() {
        val layout = ComponentLayout(
            slots = ComponentLayout.defaultSlots,
            customComponents = emptyMap(),
            fullPlayerSlots = ComponentLayout.defaultFullPlayerSlots,
        )
        val css = CssRuleTable(rules = mapOf(".main" to mapOf("arrange" to "column")))

        val dir = tmp.newFolder("cache")
        BinaryCache.write(dir, layout, css)

        val restored = BinaryCache.tryRead(dir)
        assertNotNull("缓存读取结果不应为 null", restored)
        val (restoredLayout, restoredCss) = restored!!

        // fullPlayerSlots（含 children）完整往返一致
        assertEquals("fullPlayerSlots 往返后应与写入前一致", layout.fullPlayerSlots, restoredLayout.fullPlayerSlots)
        // CSS 规则往返一致
        assertEquals(css, restoredCss)

        // 明确断言背景层容器 + 前景组件齐全（标题/副标题位于顶部 = 左上角）
        val main = restoredLayout.fullPlayerSlots.getValue("main")
        assertEquals("fp-backdrop", main[0].id)
        @Suppress("UNCHECKED_CAST")
        val children = main[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(
            listOf("fp-track-title", "fp-track-subtitle", "fp-cover", "fp-progress", "controls-row"),
            children.getValue("content").map { it.id },
        )
    }

    @Test
    fun 平铺旧格式fullPlayerSlots_序列化往返一致() {
        val layout = ComponentLayout(
            fullPlayerSlots = mapOf(
                "main" to listOf(
                    ComponentEntry("track-info"),
                    ComponentEntry("progress-bar"),
                    ComponentEntry("controls-row"),
                ),
            ),
        )

        val dir = tmp.newFolder("legacy")
        BinaryCache.write(dir, layout, CssRuleTable())

        val restored = BinaryCache.tryRead(dir)
        assertNotNull(restored)
        assertEquals(layout.fullPlayerSlots, restored!!.first.fullPlayerSlots)
    }

    @Test
    fun 缓存被篡改时_返回null并删除缓存文件() {
        val layout = ComponentLayout(fullPlayerSlots = ComponentLayout.defaultFullPlayerSlots)
        val dir = tmp.newFolder("tamper")

        BinaryCache.write(dir, layout, CssRuleTable())

        val cacheFile = File(dir, "layout.cache")
        assertTrue("缓存文件应已写入", cacheFile.isFile)
        val bytes = cacheFile.readBytes()
        // 翻转数据区一个位（避开尾部 32 字节 hash）
        bytes[16] = (bytes[16].toInt() xor 0xFF).toByte()
        cacheFile.writeBytes(bytes)

        assertNull("篡改后 tryRead 应返回 null", BinaryCache.tryRead(dir))
        assertFalse("篡改的缓存文件应被删除", cacheFile.exists())
    }
}