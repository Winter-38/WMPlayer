package com.winter.muplayer.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全屏播放器 JSON 配置解析测试。
 *
 * 验证 [LayoutParser] / [StyleConfigLoader.parseConfigObjectStatic] 对
 * 「fp-backdrop 容器 + children 前景层」新版格式与「平铺组件数组」旧版格式的解析。
 */
class FullPlayerLayoutParserTest {

    @Test
    fun 解析_fpBackdrop容器加children前景层() {
        val json = JSONObject(
            """
            {
              "main": [
                {
                  "fp-backdrop": {
                    "children": {
                      "content": [
                        "fp-track-title",
                        "fp-track-subtitle",
                        "fp-cover",
                        "fp-progress",
                        "controls-row"
                      ]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        val main = layout.fullPlayerSlots.getValue("main")
        assertEquals(1, main.size)

        val backdrop = main[0]
        assertEquals("fp-backdrop", backdrop.id)
        assertTrue(backdrop.extra["children"] is Map<*, *>)

        @Suppress("UNCHECKED_CAST")
        val children = backdrop.extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("content"), children.keys)
        assertEquals(
            listOf("fp-track-title", "fp-track-subtitle", "fp-cover", "fp-progress", "controls-row"),
            children.getValue("content").map { it.id },
        )
    }

    @Test
    fun 解析_旧版平铺组件数组_兼容() {
        val json = JSONObject(
            """
            {
              "main": ["track-info", "progress-bar", "controls-row"]
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        val main = layout.fullPlayerSlots.getValue("main")
        assertEquals(listOf("track-info", "progress-bar", "controls-row"), main.map { it.id })
        // 平铺组件不应产生 children
        assertTrue(main.all { it.extra["children"] == null })
    }

    @Test
    fun 未配置main时_回退默认细分组件组装() {
        val json = JSONObject(
            """
            {
              "app-top": ["app-name"]
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)
        // 未配置 main → 使用默认值
        assertEquals(ComponentLayout.defaultFullPlayerSlots, layout.fullPlayerSlots)

        val main = layout.fullPlayerSlots.getValue("main")
        assertEquals("fp-backdrop", main[0].id)
        @Suppress("UNCHECKED_CAST")
        val children = main[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals("fp-track-title", children.getValue("content")[0].id)
    }

    @Test
    fun 解析_children子插槽支持多key() {
        val json = JSONObject(
            """
            {
              "main": [
                {
                  "fp-backdrop": {
                    "children": {
                      "upper": ["fp-track-title"],
                      "lower": ["controls-row"]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        @Suppress("UNCHECKED_CAST")
        val children = layout.fullPlayerSlots.getValue("main")[0]
            .extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("upper", "lower"), children.keys)
        assertEquals(listOf("fp-track-title"), children.getValue("upper").map { it.id })
        assertEquals(listOf("controls-row"), children.getValue("lower").map { it.id })
    }

    @Test
    fun 完整配置_主界面与全屏共存() {
        val json = JSONObject(
            """
            {
              "slots": [
                { "app-top": ["app-name", "search-button"] },
                { "app-bottom": ["playbar"] }
              ],
              "main": [
                {
                  "fp-backdrop": {
                    "children": {
                      "content": ["fp-cover", "controls-row"]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        // 主界面 slots 解析正常
        assertEquals(setOf("app-top", "app-bottom"), layout.slots.keys)
        assertEquals(listOf("app-name", "search-button"), layout.slots.getValue("app-top").map { it.id })
        assertEquals(listOf("playbar"), layout.slots.getValue("app-bottom").map { it.id })

        // 全屏 main 解析为容器 + children
        val main = layout.fullPlayerSlots.getValue("main")
        assertEquals("fp-backdrop", main[0].id)
        @Suppress("UNCHECKED_CAST")
        val children = main[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(listOf("fp-cover", "controls-row"), children.getValue("content").map { it.id })

        // customComponents 保持为空（本配置未定义自定义组件）
        assertNotNull(layout.customComponents)
    }
}
