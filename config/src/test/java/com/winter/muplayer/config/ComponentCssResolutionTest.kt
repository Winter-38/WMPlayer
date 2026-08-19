package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 验证组件 CSS 的 cid 覆盖语义（回归测试）。
 *
 * 用户报告的场景：组件 `spacer@awa`（cid=awa），CSS 中：
 * ```css
 * #spacer { weight: 1; }
 * #awa    { height: 16px; }
 * ```
 * 期望：`#spacer` 的样式**不得覆盖** `#awa` 的样式——`height: 16px` 必须生效，
 * 即合并结果为 `{ weight: 1, height: 16px }`（属性级叠加，cid 覆盖同名属性）。
 */
class ComponentCssResolutionTest {

    /** 用户报告的核心场景：type 规则与 cid 规则属性不同名 → 两者都保留 */
    @Test
    fun cidHeightSurvivesTypeWeight() {
        val css = CssRuleTable(
            CssParser.parse("""
                #spacer { weight: 1; }
                #awa { height: 16px; }
            """.trimIndent())
        )
        val entry = ComponentEntry(id = "spacer", cid = "awa")

        val merged = resolveComponentCss(entry, css)

        // 关键断言：cid 的 height 不能被 type 的规则“吞掉”
        assertEquals("1", merged["weight"])
        assertEquals("16px", merged["height"])
    }

    /** 同名属性：cid 必须覆盖 type */
    @Test
    fun cidSameNamePropertyOverridesType() {
        val css = CssRuleTable(
            CssParser.parse("""
                #spacer { weight: 1; height: 20px; }
                #awa { height: 16px; }
            """.trimIndent())
        )
        val entry = ComponentEntry(id = "spacer", cid = "awa")

        val merged = resolveComponentCss(entry, css)

        assertEquals("16px", merged["height"])
        assertEquals("1", merged["weight"])
    }

    /** 无 cid：只应用 type 规则 */
    @Test
    fun noCidUsesTypeRuleOnly() {
        val css = CssRuleTable(CssParser.parse("#spacer { weight: 1; }"))
        val entry = ComponentEntry(id = "spacer")

        assertEquals(mapOf("weight" to "1"), resolveComponentCss(entry, css))
    }

    /** 只有 cid 规则、无 type 规则：cid 规则照常生效 */
    @Test
    fun cidRuleAloneApplies() {
        val css = CssRuleTable(CssParser.parse("#awa { height: 16px; }"))
        val entry = ComponentEntry(id = "spacer", cid = "awa")

        assertEquals(mapOf("height" to "16px"), resolveComponentCss(entry, css))
    }

    /** 规则表中没有 cid 选择器：回退到 type 规则 */
    @Test
    fun missingCidRuleFallsBackToType() {
        val css = CssRuleTable(CssParser.parse("#spacer { weight: 1; }"))
        val entry = ComponentEntry(id = "spacer", cid = "awa")

        assertEquals(mapOf("weight" to "1"), resolveComponentCss(entry, css))
    }

    /** 布局解析链：spacer@awa 必须产出 cid=awa（cid 丢失会导致 #awa 永不生效） */
    @Test
    fun layoutParserExtractsCidFromAtSyntax() {
        val entries = LayoutParser.parseSlotValue(org.json.JSONArray("[\"spacer@awa\"]"))

        assertEquals(1, entries.size)
        assertEquals("spacer", entries[0].id)
        assertEquals("awa", entries[0].cid)
        assertNull(entries[0].extra["cid"])
    }

    /** 对象形式 { "#icon@search-icon": {...} } 同样解析 cid */
    @Test
    fun layoutParserExtractsCidFromObjectKey() {
        val entries = LayoutParser.parseSlotValue(
            org.json.JSONObject("""{ "slot": [ { "#icon@search-icon": { "icon": "ic_search" } } ] }""")
                .getJSONArray("slot")
        )

        assertEquals(1, entries.size)
        assertEquals("icon", entries[0].id)
        assertEquals("search-icon", entries[0].cid)
    }
}
