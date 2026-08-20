package com.winter.muplayer.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 初始默认布局文件模板测试。
 *
 * 验证 [StyleConfigLoader.defaultMainJson] / [StyleConfigLoader.defaultStylesCss]
 * （首次启动由 `writeDefaultsIfMissing` 写入磁盘的模板，与手机设备实测配置一致）：
 * - JSON 可被解析器正确解析，主界面 slot 与全屏播放器结构符合预期
 * - CSS 可被 [CssParser] 解析，关键规则齐全
 * - 模板引用的组件 id 全部在 ui 模块注册清单内
 */
class DefaultLayoutTemplateTest {

    private fun parseMainJson(): JSONObject =
        JSONObject(LayoutParser.removeComments(StyleConfigLoader.defaultMainJson()))

    @Test
    fun mainJson_主界面slots结构正确() {
        val layout = StyleConfigLoader.parseConfigObjectStatic(parseMainJson())

        // app-top：左侧占位（cid=m-top-spacer）+ 应用名 + 弹性占位 + 搜索 + 设置
        assertEquals(
            listOf("spacer", "app-name", "spacer", "search-button", "setting-button"),
            layout.slots.getValue("app-top").map { it.id },
        )
        assertEquals("m-top-spacer", layout.slots.getValue("app-top")[0].cid)
        assertEquals(
            "主区域应包含 分类标签/排序/播放列表",
            listOf("tab-bar", "sort", "playlist"),
            layout.slots.getValue("app-center").map { it.id },
        )
        assertEquals(
            "底部栏应包含迷你播放栏",
            listOf("playbar"),
            layout.slots.getValue("app-bottom").map { it.id },
        )
    }

    @Test
    fun mainJson_全屏播放器为背景层加命名子slot结构() {
        val layout = StyleConfigLoader.parseConfigObjectStatic(parseMainJson())
        val fullPlayer = layout.fullPlayerSlots.getValue("full-player")

        // 顶层唯一组件：fp-backdrop 背景层容器
        assertEquals(1, fullPlayer.size)
        val backdrop = fullPlayer[0]
        assertEquals("fp-backdrop", backdrop.id)
        assertTrue("fp-backdrop 必须携带 children 前景层", backdrop.extra["children"] is Map<*, *>)

        // children 数组自动包装为单个子 slot（名 = 容器 id）
        @Suppress("UNCHECKED_CAST")
        val children = backdrop.extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("fp-backdrop"), children.keys)
        val content = children.getValue("fp-backdrop").map { it.id }

        // 标题 → 副标题 → 封面（命名子 slot main-cover）→ 进度条 → 按钮组 → 底部留白
        assertEquals(
            listOf(
                "fp-title", "fp-subtitle", LayoutParser.ANONYMOUS_CONTAINER,
                "fp-progress", "controls-row", "spacer",
            ),
            content,
        )
        assertEquals("fp-bottom-spacer", children.getValue("fp-backdrop")[5].cid)

        // main-cover 命名子 slot：内部只有 fp-cover
        @Suppress("UNCHECKED_CAST")
        val coverChildren = (children.getValue("fp-backdrop")[2].extra["children"] as Map<String, List<ComponentEntry>>)
        assertEquals(setOf("main-cover"), coverChildren.keys)
        assertEquals(listOf("fp-cover"), coverChildren.getValue("main-cover").map { it.id })
    }

    @Test
    fun stylesCss_可解析且关键规则齐全() {
        val rules = CssParser.parse(StyleConfigLoader.defaultStylesCss())

        // 外层方向
        assertEquals("column", rules[".main"]?.get("arrange"))

        // 顶部栏
        assertEquals("row", rules[".app-top"]?.get("arrange"))
        assertEquals("0", rules[".app-top"]?.get("weight"))
        assertEquals("1", rules["#spacer"]?.get("weight"))
        assertTrue(rules.containsKey("#m-top-spacer"))
        assertTrue(rules.containsKey("#app-name"))
        assertTrue(rules.containsKey("#search-button"))
        assertTrue(rules.containsKey("#setting-button"))

        // 主区域 / 分类标签（设备配置为横向 TabRow）/ 底部栏
        assertEquals("column", rules[".app-center"]?.get("arrange"))
        assertEquals("1", rules[".app-center"]?.get("weight"))
        assertEquals("row", rules["#tab-bar"]?.get("display"))
        assertEquals("row", rules[".app-bottom"]?.get("arrange"))
        assertEquals("0", rules[".app-bottom"]?.get("weight"))

        // 全屏播放器：外层纵向、前景子 slot 内边距、封面命名子 slot 居中、底部留白
        assertEquals("column", rules[".full-player"]?.get("arrange"))
        assertTrue(rules[".fp-backdrop"]?.containsKey("padding") == true)
        assertEquals("1", rules[".main-cover"]?.get("weight"))
        assertEquals("center", rules[".main-cover"]?.get("justify-content"))
        assertEquals("32px", rules["#fp-bottom-spacer"]?.get("height"))
    }

    @Test
    fun 模板引用的组件id均在注册清单内() {
        val layout = StyleConfigLoader.parseConfigObjectStatic(parseMainJson())
        val ids = collectAllComponentIds(layout.slots) + collectAllComponentIds(layout.fullPlayerSlots)

        // 与 ui 模块 BuiltInComponents registerBuiltInComponents 注册表对应
        val registered = setOf(
            "app-name", "spacer", "search-button", "setting-button",
            "tab-bar", "sort", "playlist", "playbar",
            "fp-backdrop", "fp-cover", "fp-title", "fp-subtitle", "fp-progress",
            "controls-row",
        )
        val unknown = ids - registered
        assertTrue("模板引用了未注册组件: $unknown", unknown.isEmpty())
    }

    /** 递归收集 slots 中引用的全部组件 id（含 children 嵌套，过滤匿名容器占位） */
    private fun collectAllComponentIds(slots: Map<String, List<ComponentEntry>>): Set<String> {
        val result = mutableSetOf<String>()
        fun walk(entries: List<ComponentEntry>) {
            for (e in entries) {
                if (e.id != LayoutParser.ANONYMOUS_CONTAINER) result += e.id
                val children = e.extra["children"]
                if (children is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    walk((children as Map<String, List<ComponentEntry>>).values.flatten())
                }
            }
        }
        slots.values.forEach { walk(it) }
        return result
    }
}
