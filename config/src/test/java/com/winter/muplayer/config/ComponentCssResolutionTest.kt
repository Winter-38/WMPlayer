package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ─────────────── 组件内联 style（S1：布局 JSON 组件声明的 "style" 字段） ───────────────

    /** 内联 style 并入组件 CSS，优先级最高（覆盖规则表） */
    @Test
    fun inlineStyleMergesAndOverridesCssRules() {
        val css = CssRuleTable(CssParser.parse("""
            #mt-swatch-1 { size: 48px; border-radius: 24px; }
        """.trimIndent()))
        val entry = ComponentEntry(
            id = "mt-swatch-1",
            extra = mapOf(
                "style" to org.json.JSONObject(
                    """{"background-color": "#F44336", "size": "40px"}"""
                )
            ),
        )

        val merged = resolveComponentCss(entry, css)

        // 内联 style 覆盖同名规则表属性，并新增规则表中没有的属性
        assertEquals("#F44336", merged["background-color"])
        assertEquals("40px", merged["size"])
        assertEquals("24px", merged["border-radius"])
    }

    /** 无内联 style 时行为不变（只应用规则表） */
    @Test
    fun noInlineStyleFallsBackToRulesOnly() {
        val css = CssRuleTable(CssParser.parse("#mt-swatch-1 { size: 48px; }"))
        val entry = ComponentEntry(id = "mt-swatch-1")

        assertEquals(mapOf("size" to "48px"), resolveComponentCss(entry, css))
    }

    /** 对象 key 组件声明（如生成器色块）解析后携带内联 style，且为 org.json.JSONObject */
    @Test
    fun objectKeyComponentCarriesInlineStyle() {
        val entries = LayoutParser.parseSlotValue(
            org.json.JSONArray(
                """[ { "mt-swatch-1": { "style": { "background-color": "#F44336", "size": "40px", "border-radius": "20px" } } } ]"""
            )
        )

        assertEquals(1, entries.size)
        assertEquals("mt-swatch-1", entries[0].id)
        val style = entries[0].extra["style"]
        assertTrue("内联 style 应为 org.json.JSONObject", style is org.json.JSONObject)
        assertEquals("#F44336", (style as org.json.JSONObject).getString("background-color"))
    }

    // ─────────────── 统一样式文件（style.css）+ text content / cid 规则 ───────────────

    /** CssParser 可解析插件 style.css：content 字符串值（含引号/特殊字符）原样保留 */
    @Test
    fun cssParserKeepsContentStringValue() {
        val rules = CssParser.parse("""
            #gen-hint {
              content: "点击下方色块选择主色（各 10 级色阶）";
              font-size: 13px;
              color: #666666;
            }
        """.trimIndent())

        val rule = rules["#gen-hint"] ?: error("缺少 #gen-hint 规则")
        assertEquals("\"点击下方色块选择主色（各 10 级色阶）\"", rule["content"])
        assertEquals("13px", rule["font-size"])
        assertEquals("#666666", rule["color"])
    }

    /** cid 规则（#mt-title）对 text@mt-title 生效，供 text 组件 CSS content 使用 */
    @Test
    fun cidRuleAppliesContentToTextComponent() {
        val css = CssRuleTable(
            CssParser.parse("#mt-title { content: \"Material 主题生成器\"; font-size: 20px; }")
        )
        val entry = ComponentEntry(id = "text", cid = "mt-title")

        val merged = resolveComponentCss(entry, css)

        assertEquals("\"Material 主题生成器\"", merged["content"])
        assertEquals("20px", merged["font-size"])
    }

    // ─────────────── # 前缀引用（旧文件兼容） ───────────────

    /** `#mt-swatch-1` 解析为 isCustom=true（解释 SlotRenderer 旧版跳过行为） */
    @Test
    fun hashPrefixedRefParsesAsCustom() {
        val entries = LayoutParser.parseSlotValue(
            org.json.JSONArray("""[ "#mt-swatch-1" ]""")
        )
        assertEquals(1, entries.size)
        assertEquals("mt-swatch-1", entries[0].id)
        assertTrue("带 # 前缀应标记 isCustom", entries[0].isCustom)
    }

    /** SlotRenderer 回退依赖 isRegistered：插件注册后 # 前缀引用仍可渲染 */
    @Test
    fun registryLookupBacksUpHashPrefixedRef() {
        // 模拟插件注册色块组件
        ComponentRegistry.register("mt-swatch-1") { }
        try {
            assertTrue("注册后 isRegistered 应为 true", ComponentRegistry.isRegistered("mt-swatch-1"))
        } finally {
            ComponentRegistry.unregister("mt-swatch-1")
        }
        assertTrue("注销后应为 false", !ComponentRegistry.isRegistered("mt-swatch-1"))
    }
}
