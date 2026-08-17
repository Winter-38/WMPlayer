package com.winter.muplayer.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 格式 v2（对象化 slot）解析测试。
 *
 * 覆盖：
 * - 界面内容为对象 `{}`，不同名字对应不同 slot
 * - 组件数组内嵌 `{ "name": ..., "children": [...] }` 命名子 slot
 * - slot 名与 slot 型组件 id（fp-backdrop）一致时解析为容器组件
 * - 旧格式（数组）兼容
 */
class NewFormatLayoutParserTest {

    @Test
    fun 对象形式slots_解析为主界面slot字典() {
        val json = JSONObject(
            """
            {
              "slots": {
                "app-top": ["app-name", "spacer"],
                "app-center": ["tab-bar", "sort", "playlist"],
                "app-bottom": ["playbar"]
              }
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        assertEquals(setOf("app-top", "app-center", "app-bottom"), layout.slots.keys)
        assertEquals(listOf("app-name", "spacer"), layout.slots.getValue("app-top").map { it.id })
        assertEquals(listOf("playbar"), layout.slots.getValue("app-bottom").map { it.id })
    }

    @Test
    fun 组件数组内嵌name加children命名子slot() {
        val json = JSONObject(
            """
            {
              "slots": {
                "screen": [
                  "compose1",
                  { "name": "children-slot", "children": ["child-1", "child-2"] },
                  "compose2"
                ]
              }
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        val screen = layout.slots.getValue("screen")
        assertEquals(listOf("compose1", LayoutParser.ANONYMOUS_CONTAINER, "compose2"), screen.map { it.id })

        @Suppress("UNCHECKED_CAST")
        val children = screen[1].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals("命名子 slot 名用于 CSS 定位（.children-slot）", setOf("children-slot"), children.keys)
        assertEquals(listOf("child-1", "child-2"), children.getValue("children-slot").map { it.id })
    }

    @Test
    fun slot名与slot型组件一致_解析为容器组件_数组形式() {
        val json = JSONObject(
            """
            {
              "main": {
                "fp-backdrop": ["fp-track-title", "fp-cover"]
              }
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        val main = layout.fullPlayerSlots.getValue("main")
        assertEquals(1, main.size)
        assertEquals("fp-backdrop", main[0].id)

        @Suppress("UNCHECKED_CAST")
        val children = main[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals("数组形式 children 包装为匿名 content 子 slot", setOf("content"), children.keys)
        assertEquals(listOf("fp-track-title", "fp-cover"), children.getValue("content").map { it.id })
    }

    @Test
    fun slot名与slot型组件一致_解析为容器组件_对象形式() {
        val json = JSONObject(
            """
            {
              "main": {
                "fp-backdrop": {
                  "main-info": ["fp-track-title", "fp-track-subtitle"],
                  "main-cover": ["fp-cover"]
                }
              }
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        val main = layout.fullPlayerSlots.getValue("main")
        assertEquals("fp-backdrop", main[0].id)
        @Suppress("UNCHECKED_CAST")
        val children = main[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("main-info", "main-cover"), children.keys)
        assertEquals(
            listOf("fp-track-title", "fp-track-subtitle"),
            children.getValue("main-info").map { it.id },
        )
        assertEquals(listOf("fp-cover"), children.getValue("main-cover").map { it.id })
    }

    @Test
    fun name为slot型组件_解析为容器组件() {
        val json = JSONObject(
            """
            {
              "slots": {
                "screen": [
                  "title",
                  { "name": "fp-backdrop", "children": ["cover", "controls"] },
                  "tail"
                ]
              }
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        val screen = layout.slots.getValue("screen")
        assertEquals(listOf("title", "fp-backdrop", "tail"), screen.map { it.id })

        val backdrop = screen[1]
        assertEquals("fp-backdrop", backdrop.id)
        @Suppress("UNCHECKED_CAST")
        val children = backdrop.extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("content"), children.keys)
        assertEquals(listOf("cover", "controls"), children.getValue("content").map { it.id })
    }

    @Test
    fun 多key对象视为子slot字典_匿名容器() {
        val json = JSONObject(
            """
            {
              "slots": {
                "screen": {
                  "top": ["app-name"],
                  "body": ["playlist"]
                }
              }
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        val screen = layout.slots.getValue("screen")
        assertEquals(listOf(LayoutParser.ANONYMOUS_CONTAINER), screen.map { it.id })
        @Suppress("UNCHECKED_CAST")
        val children = screen[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("top", "body"), children.keys)
    }

    @Test
    fun 旧格式_slots数组_仍兼容() {
        val json = JSONObject(
            """
            {
              "slots": [
                { "app-top": ["app-name", "search-button"] },
                { "app-bottom": ["playbar"] }
              ]
            }
            """.trimIndent(),
        )

        val layout = StyleConfigLoader.parseConfigObjectStatic(json)

        assertEquals(setOf("app-top", "app-bottom"), layout.slots.keys)
        assertEquals(listOf("app-name", "search-button"), layout.slots.getValue("app-top").map { it.id })
    }

    @Test
    fun 旧格式_fpBackdrop加children对象包装_仍兼容() {
        // 设备当前使用的旧格式：{ "fp-backdrop": { "children": { ... } } }
        val json = JSONObject(
            """
            {
              "main": [
                {
                  "fp-backdrop": {
                    "children": {
                      "main-info": ["fp-track-title", "fp-track-subtitle"],
                      "main-cover": ["fp-cover"]
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
        assertEquals("fp-backdrop", main[0].id)
        @Suppress("UNCHECKED_CAST")
        val children = main[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("main-info", "main-cover"), children.keys)
        assertEquals(listOf("fp-cover"), children.getValue("main-cover").map { it.id })
    }
}
