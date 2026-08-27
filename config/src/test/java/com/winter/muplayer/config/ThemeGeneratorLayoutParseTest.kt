package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 端到端格式验证：用宿主真实解析链路解析 theme-generator 插件的
 * main.json（slot 结构）与 style.css（统一样式），确认格式无解析错误。
 *
 * 若此测试失败，即说明插件 JSON/CSS 格式与宿主解析器不兼容。
 */
class ThemeGeneratorLayoutParseTest {

    private val pluginDir = File("/home/winter/WMPlayer/lua-plugin/theme-generator")

    private fun parseMainJson(): ComponentLayout {
        val text = File(pluginDir, "main.json").readText()
        val root = org.json.JSONObject(LayoutParser.removeComments(text))
        return StyleConfigLoader.parseConfigObjectStatic(root)
    }

    @Test
    fun mainJson_slot结构与组件引用解析正确() {
        val layout = parseMainJson()

        // main 为对象形式：slot 字典（JVM org.json 无序，Android 保持 JSON 声明顺序）
        assertEquals(
            setOf("header", "swatches-a", "swatches-b", "results"),
            layout.slots.keys.toSet(),
        )

        // header：text@gen-title / text@gen-hint（id=text，cid=gen-title/gen-hint）
        val header = layout.slots.getValue("header")
        assertEquals(listOf("text", "text"), header.map { it.id })
        assertEquals(listOf("gen-title", "gen-hint"), header.map { it.cid })

        // 色板：12 个色块组件
        val swatches = layout.slots.getValue("swatches-a") + layout.slots.getValue("swatches-b")
        assertEquals(12, swatches.size)
        assertEquals(
            (1..12).map { "mt-swatch-$it" },
            swatches.map { it.id },
        )
        assertTrue(swatches.all { it.cid == null })

        // results：标题 + 3 个结果组件
        val results = layout.slots.getValue("results")
        assertEquals(listOf("text", "mt-result-primary", "mt-result-secondary", "mt-result-tertiary"), results.map { it.id })
    }

    @Test
    fun styleCss_全部规则可解析() {
        val rules = CssParser.parse(File(pluginDir, "style.css").readText())

        // slot 级规则
        for (sel in listOf(".main", ".header", ".swatches-a", ".swatches-b", ".results")) {
            assertNotNull("缺少规则 $sel", rules[sel])
        }
        assertEquals("column", rules[".main"]?.get("arrange"))
        assertEquals("row", rules[".swatches-a"]?.get("arrange"))

        // 组件级规则：text 的 content / 色块的颜色
        assertEquals("\"Material 主题生成器\"", rules["#gen-title"]?.get("content"))
        assertEquals("18px", rules["#gen-title"]?.get("font-size"))
        assertEquals("#666666", rules["#gen-hint"]?.get("color"))
        for (i in 1..12) {
            val rule = rules["#mt-swatch-$i"] ?: error("缺少规则 #mt-swatch-$i")
            assertNotNull("色块 $i 缺少 color", rule["color"])
            assertEquals("40px", rule["size"])
            assertEquals("20px", rule["border-radius"])
        }
    }

    @Test
    fun 组件级Css解析_与宿主loadLayout合并逻辑一致() {
        // 复刻 PluginUiHost.loadLayout 的合并：JSON 内联 style（无） + style.css
        val jsonCss = emptyMap<String, Map<String, String>>()
        val fileCss = CssParser.parse(File(pluginDir, "style.css").readText())
        val css = CssRuleTable(jsonCss + fileCss)

        // text@gen-title → 命中 #gen-title（content/font-size/color）
        val titleEntry = ComponentEntry(id = "text", cid = "gen-title")
        val titleCss = resolveComponentCss(titleEntry, css)
        assertEquals("\"Material 主题生成器\"", titleCss["content"])
        assertEquals("18px", titleCss["font-size"])
        assertEquals("#1A1A1A", titleCss["color"])

        // 色块 mt-swatch-1 → 命中 #mt-swatch-1（color/size/border-radius）
        val swatchEntry = ComponentEntry(id = "mt-swatch-1")
        val swatchCss = resolveComponentCss(swatchEntry, css)
        assertEquals("#F44336", swatchCss["color"])
        assertEquals("40px", swatchCss["size"])
        assertEquals("20px", swatchCss["border-radius"])
    }
}
