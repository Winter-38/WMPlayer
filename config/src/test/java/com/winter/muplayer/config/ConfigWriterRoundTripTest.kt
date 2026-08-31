package com.winter.muplayer.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConfigWriter 往返测试 —— 编辑器把内存模型序列化回磁盘格式后，
 * 必须能被 LayoutParser / CssParser 原样读回（结构等价）。
 *
 * 覆盖：默认模板、自定义组件定义、组件 cid / extra、命名子 slot（归一化）、
 * 容器组件（fp-backdrop）、空规则表、CSS 顺序稳定性。
 *
 * 注意：命名子 slot `{ "name": X, "children": [...] }` 与子 slot 声明
 * `{ X: [...] }` 在解析后语义完全等价，序列化输出后者 —— 因此用结构比较
 * （ComponentLayout 层面）而非 JSON 文本比较。
 */
class ConfigWriterRoundTripTest {

    private fun parse(text: String): JSONObject =
        JSONObject(LayoutParser.removeComments(text))

    private fun parseLayout(text: String): ComponentLayout =
        StyleConfigLoader.parseConfigObjectStatic(parse(text))

    // ── 布局往返（结构等价） ──

    @Test
    fun 默认mainJson_序列化后重新解析结构等价() {
        val layout = parseLayout(StyleConfigLoader.defaultMainJson())
        val roundTrip = parseLayout(ConfigWriter.layoutToJson(layout))
        assertLayoutEquals(layout, roundTrip)
    }

    @Test
    fun 自定义组件定义_往返保持() {
        val json = """
        {
          "#my-widget": { "icon": "ic_music_note", "size": 24 },
          "main": {
            "app-top": [ "app-name", "#my-widget" ]
          }
        }
        """.trimIndent()
        val layout = parseLayout(json)
        assertEquals(setOf("my-widget"), layout.customComponents.keys)
        assertEquals("ic_music_note", layout.customComponents.getValue("my-widget")["icon"])

        val roundTrip = parseLayout(ConfigWriter.layoutToJson(layout))
        assertLayoutEquals(layout, roundTrip)
    }

    @Test
    fun 组件cid与extra_往返保持() {
        val json = """
        {
          "main": {
            "app-top": [
              "app-name",
              { "icon@search-icon": { "icon": "ic_search" } },
              "#custom@my-id"
            ]
          }
        }
        """.trimIndent()
        val layout = parseLayout(json)
        val entries = layout.slots.getValue("app-top")
        assertEquals("app-name", entries[0].id)
        assertEquals(null, entries[0].cid)
        // 带 extra + cid 的对象组件
        assertEquals("icon", entries[1].id)
        assertEquals("search-icon", entries[1].cid)
        assertEquals("ic_search", entries[1].extra["icon"])
        // 自定义组件 + cid 字符串形式
        assertEquals("custom", entries[2].id)
        assertEquals("my-id", entries[2].cid)
        assertTrue(entries[2].isCustom)

        val roundTrip = parseLayout(ConfigWriter.layoutToJson(layout))
        assertLayoutEquals(layout, roundTrip)
    }

    @Test
    fun 命名子slot容器_往返保持() {
        val json = """
        {
          "main": {
            "app-center": [
              { "name": "content", "children": ["tab-bar", "sort", "playlist"] },
              "playbar"
            ]
          }
        }
        """.trimIndent()
        val layout = parseLayout(json)
        val center = layout.slots.getValue("app-center")
        // 原格式解析为匿名容器：children key = 子 slot 名
        assertEquals(LayoutParser.ANONYMOUS_CONTAINER, center[0].id)
        @Suppress("UNCHECKED_CAST")
        val children0 = center[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("content"), children0.keys)

        val roundTrip = parseLayout(ConfigWriter.layoutToJson(layout))
        val center2 = roundTrip.slots.getValue("app-center")
        assertEquals(2, center2.size)
        assertEquals(LayoutParser.ANONYMOUS_CONTAINER, center2[0].id)
        @Suppress("UNCHECKED_CAST")
        val children2 = center2[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("content"), children2.keys)
        assertEquals(listOf("tab-bar", "sort", "playlist"), children2.getValue("content").map { it.id })
        assertEquals("playbar", center2[1].id)
        assertLayoutEquals(layout, roundTrip)
    }

    @Test
    fun 主界面slot内命名子slot与普通组件混合_编辑器更新往返保持() {
        // 用户场景：{name: "example", children: ["example", "example"]} 出现在主界面 slot 中
        val json = """
        {
          "main": {
            "app-center": [
              "tab-bar",
              { "name": "example", "children": ["example", "example"] },
              "playlist"
            ]
          }
        }
        """.trimIndent()
        val layout = parseLayout(json)
        val center = layout.slots.getValue("app-center")
        // 三段：普通组件 / 命名子 slot 容器 / 普通组件
        assertEquals(listOf("tab-bar", LayoutParser.ANONYMOUS_CONTAINER, "playlist"), center.map { it.id })
        @Suppress("UNCHECKED_CAST")
        val children = center[1].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals("子 slot 名应为 name 值", setOf("example"), children.keys)
        assertEquals("子组件列表正确", listOf("example", "example"), children.getValue("example").map { it.id })

        // 模拟编辑器在子 slot 中追加一个组件（updateNestedChild 的模型操作）后序列化往返
        val childMap = children.toMutableMap()
        childMap["example"] = children.getValue("example") + ComponentEntry("example")
        val updatedEntry = center[1].copy(extra = center[1].extra + ("children" to childMap))
        val updatedLayout = layout.copy(
            slots = layout.slots + ("app-center" to listOf(center[0], updatedEntry, center[2])),
        )
        val roundTrip = parseLayout(ConfigWriter.layoutToJson(updatedLayout))
        assertLayoutEquals(updatedLayout, roundTrip)
    }

    @Test
    fun 容器组件fp_backdrop_往返保持() {
        val layout = parseLayout(StyleConfigLoader.defaultMainJson())
        val fp = layout.fullPlayerSlots.getValue("full-player")
        assertEquals("fp-backdrop", fp[0].id)

        val roundTrip = parseLayout(ConfigWriter.layoutToJson(layout))
        val fp2 = roundTrip.fullPlayerSlots.getValue("full-player")
        assertEquals(1, fp2.size)
        assertEquals("fp-backdrop", fp2[0].id)
        @Suppress("UNCHECKED_CAST")
        val children2 = fp2[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("fp-space1", "fp-main", "fp-space2"), children2.keys)
        assertEquals(
            listOf("spacer", "fp-title", "fp-subtitle", "spacer", "fp-cover", "spacer", "fp-progress"),
            children2.getValue("fp-main").map { it.id }.take(7),
        )
        assertLayoutEquals(layout, roundTrip)
    }

    @Test
    fun 新增slot_往返保持() {
        val layout = parseLayout(StyleConfigLoader.defaultMainJson())
        val withExtra = layout.copy(slots = layout.slots + ("my-slot" to listOf(ComponentEntry("spacer"))))
        val roundTrip = parseLayout(ConfigWriter.layoutToJson(withExtra))
        assertLayoutEquals(withExtra, roundTrip)
        assertEquals(1, roundTrip.slots.getValue("my-slot").size)
    }

    @Test
    fun 组件extra带children_往返保持() {
        val json = """
        {
          "main": {
            "app-center": [
              { "content-slot": ["tab-bar", { "sort-slot": ["sort"] }], "bottom": ["playbar"] }
            ]
          }
        }
        """.trimIndent()
        val layout = parseLayout(json)
        val roundTrip = parseLayout(ConfigWriter.layoutToJson(layout))
        assertLayoutEquals(layout, roundTrip)
    }

    // ── CSS 往返 ──

    @Test
    fun 默认stylesCss_序列化后重新解析等价() {
        val rules1 = CssParser.parse(StyleConfigLoader.defaultStylesCss())
        val text = ConfigWriter.cssTableToText(CssRuleTable(rules = rules1))
        val rules2 = CssParser.parse(text)
        assertEquals(rules1, rules2)
    }

    @Test
    fun css_属性与选择器顺序稳定且可解析() {
        val rules = CssParser.parse(StyleConfigLoader.defaultStylesCss())
        val text = ConfigWriter.cssTableToText(CssRuleTable(rules = rules))
        // 两次序列化输出完全一致（确定性）
        assertEquals(text, ConfigWriter.cssTableToText(CssRuleTable(rules = rules)))

        val lines = text.lineSequence().filter { it.startsWith(".") || it.startsWith("#") }.toList()
        assertTrue("存在选择器行: $lines", lines.isNotEmpty())
        // 最前是外层方向选择器（.main 或 .full-player），区域在前、组件在后
        assertTrue("首个选择器应为 .main 或 .full-player，实际: ${lines[0]}",
            lines[0].startsWith(".main") || lines[0].startsWith(".full-player"))
        val firstHash = lines.indexOfFirst { it.startsWith("#") }
        val lastDot = lines.indexOfLast { it.startsWith(".") }
        assertTrue("区域选择器应全部在组件选择器之前；lastDot=$lastDot firstHash=$firstHash lines=$lines", lastDot < firstHash)
    }

    @Test
    fun css_空规则表输出为空() {
        assertEquals("", ConfigWriter.cssTableToText(CssRuleTable(emptyMap())))
    }

    // ── 辅助：ComponentLayout 结构等价比较 ──

    private fun assertLayoutEquals(a: ComponentLayout, b: ComponentLayout) {
        assertEquals("slots keys", a.slots.keys, b.slots.keys)
        a.slots.keys.forEach { k ->
            assertEntriesEquals("slots.$k", a.slots.getValue(k), b.slots.getValue(k))
        }
        assertEquals("customComponents", a.customComponents, b.customComponents)
        assertEquals("fullPlayerSlots keys", a.fullPlayerSlots.keys, b.fullPlayerSlots.keys)
        a.fullPlayerSlots.keys.forEach { k ->
            assertEntriesEquals("fullPlayerSlots.$k", a.fullPlayerSlots.getValue(k), b.fullPlayerSlots.getValue(k))
        }
    }

    private fun assertEntriesEquals(path: String, a: List<ComponentEntry>, b: List<ComponentEntry>) {
        assertEquals("$path.size", a.size, b.size)
        a.zip(b).forEachIndexed { i, (x, y) ->
            val p = "$path[$i]"
            assertEquals("$p.id", x.id, y.id)
            assertEquals("$p.cid", x.cid, y.cid)
            assertEquals("$p.isCustom", x.isCustom, y.isCustom)
            assertEquals("$p.extra.keys", x.extra.keys, y.extra.keys)
            x.extra.forEach { (k, v) ->
                val w = y.extra[k]
                if (v is Map<*, *> && w is Map<*, *>) {
                    assertEquals("$p.extra.$k.keys", v.keys, w.keys)
                    v.keys.forEach { mk ->
                        @Suppress("UNCHECKED_CAST")
                        val va = (v[mk] as List<*>).map { it as ComponentEntry }
                        @Suppress("UNCHECKED_CAST")
                        val vb = (w[mk] as List<*>).map { it as ComponentEntry }
                        assertEntriesEquals("$p.extra.$k.$mk", va, vb)
                    }
                } else {
                    assertEquals("$p.extra.$k", v, w)
                }
            }
        }
    }
}
