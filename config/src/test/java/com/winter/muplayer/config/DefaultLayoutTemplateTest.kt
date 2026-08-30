package com.winter.muplayer.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 初始默认布局文件模板测试（回退版：上一个 commit 的设备实测布局）。
 *
 * 验证 [StyleConfigLoader.defaultMainJson] / [StyleConfigLoader.defaultStylesCss]
 * （首次启动由 `writeDefaultsIfMissing` 写入磁盘的模板）：
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

        // app-top：应用名 + 弹性占位 + 搜索 + 设置
        assertEquals(
            listOf("app-name", "spacer", "search-button", "setting-button"),
            layout.slots.getValue("app-top").map { it.id },
        )
        assertEquals(
            "主区域应包含 分类标签/排序/播放列表",
            listOf("tab-bar", "sort", "playlist"),
            layout.slots.getValue("app-center").map { it.id },
        )
        assertEquals(
            "底部栏应包含迷你播放栏（playbar 聚合组件）",
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

        // children 为命名子 slot：fp-space1 / fp-main / fp-space2（左右留白 + 主内容）
        @Suppress("UNCHECKED_CAST")
        val children = backdrop.extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(setOf("fp-space1", "fp-main", "fp-space2"), children.keys)

        // fp-main 内容顺序：顶部留白 → 标题 → 副标题 → 弹性 → 封面 → 弹性 → 进度条 → 留白 → 按钮组 → 底部留白
        val main = children.getValue("fp-main").map { it.id }
        assertEquals(
            listOf(
                "spacer", "fp-title", "fp-subtitle", "spacer",
                "fp-cover", "spacer", "fp-progress", "spacer",
                LayoutParser.ANONYMOUS_CONTAINER, "spacer",
            ),
            main,
        )
        // 按钮组命名子 slot：横向居中排列播放控制
        @Suppress("UNCHECKED_CAST")
        val buttonChildren = (children.getValue("fp-main")[8].extra["children"]
            as Map<String, List<ComponentEntry>>).getValue("button")
        assertEquals(
            listOf("playmode-button", "prev-button", "play-button", "next-button", "queue-button"),
            buttonChildren.map { it.id },
        )
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

        // 主区域 / 底部迷你播放栏：线性布局
        assertEquals("column", rules[".app-center"]?.get("arrange"))
        assertEquals("1", rules[".app-center"]?.get("weight"))
        assertEquals("row", rules[".app-bottom"]?.get("arrange"))
        assertEquals("0", rules[".app-bottom"]?.get("weight"))
        // 迷你栏半透明渲染样式（semi-tran），默认不透明
        assertEquals("none", rules["#playbar"]?.get("render-style"))

        // 全屏播放器：外层纵向、fp-backdrop 前景横向 + 内边距
        assertEquals("column", rules[".full-player"]?.get("arrange"))
        assertEquals("row", rules["#fp-backdrop"]?.get("arrange"))
        assertTrue(rules["#fp-backdrop"]?.containsKey("padding") == true)

        // 命名子 slot：左右留白 weight 0、主内容纵向、按钮组横向居中
        assertEquals("0", rules[".fp-space1"]?.get("weight"))
        assertEquals("0", rules[".fp-space2"]?.get("weight"))
        assertEquals("column", rules[".fp-main"]?.get("arrange"))
        assertEquals("row", rules[".button"]?.get("arrange"))
        assertEquals("center", rules[".button"]?.get("justify-content"))

        // 封面与留白高度
        assertEquals("320px", rules["#fp-cover"]?.get("size"))
        assertEquals("32px", rules["#fp-top-spacer"]?.get("height"))
        assertEquals("100px", rules["#fp-bottom-spacer"]?.get("height"))
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
            "playmode-button", "prev-button", "play-button", "next-button", "queue-button",
            // 渲染器特判容器（非 ComponentRegistry 组件）：毛玻璃镜像容器
            "backdrop-blur",
        )
        val unknown = ids - registered
        assertTrue("模板引用了未注册组件: $unknown", unknown.isEmpty())
    }

    @Test
    fun 默认模板为线性布局且semitran可选() {
        // 默认布局：app-center 线性 + app-bottom 迷你栏（不再默认叠放/毛玻璃）
        val layout = StyleConfigLoader.parseConfigObjectStatic(
            JSONObject(LayoutParser.removeComments(StyleConfigLoader.defaultMainJson()))
        )
        assertEquals(
            listOf("tab-bar", "sort", "playlist"),
            layout.slots.getValue("app-center").map { it.id },
        )
        // CSS 模板定义 semi-tran 半透明渲染样式值，默认 none
        val rules = CssParser.parse(StyleConfigLoader.defaultStylesCss())
        assertEquals("none", rules["#playbar"]?.get("render-style"))
        assertTrue("模板 CSS 应含 semi-tran 值", StyleConfigLoader.defaultStylesCss().contains("semi-tran"))
        // backdrop-blur 毛玻璃容器仍是可选能力（渲染器特判），但默认模板不再引用
        assertTrue(!StyleConfigLoader.defaultMainJson().contains("backdrop-blur"))
    }

    @Test
    fun overlay浮层布局可解析且playbar自包含毛玻璃() {
        // 三态切换中「半透明/毛玻璃」的目标布局：前景列表 + playbar 浮层（毛玻璃镜像由 playbar 自包含）
        val overlayJson = """
        {
          "main": {
            "app-center": [
              { "name": "content", "children": ["tab-bar", "sort", "playlist"] },
              "playbar"
            ]
          }
        }
        """.trimIndent()
        val layout = StyleConfigLoader.parseConfigObjectStatic(
            JSONObject(LayoutParser.removeComments(overlayJson))
        )
        val center = layout.slots.getValue("app-center")
        // 两层：内容层（前景列表）→ playbar 浮层
        assertEquals(
            listOf(LayoutParser.ANONYMOUS_CONTAINER, "playbar"),
            center.map { it.id },
        )
        @Suppress("UNCHECKED_CAST")
        val contentChildren = center[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals(listOf("tab-bar", "sort", "playlist"), contentChildren.getValue("content").map { it.id })
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
