package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全屏播放器默认组件组合测试。
 *
 * 验证 [ComponentLayout.defaultFullPlayerSlots] 由细分组件正确组装：
 * `fp-backdrop` 作为背景层容器（children 前景层叠加其上），
 * 前景依次为 主封面 / 标题 / 歌手+专辑 / 进度条 / 播放操控按钮组。
 */
class FullPlayerSlotsTest {

    /** 全屏播放器默认组装中应出现的所有组件 id（与 ui 模块 registerBuiltInComponents 注册表对应） */
    private val expectedComponentIds = setOf(
        "fp-backdrop", // 背景层容器（封面模糊背景）
        "fp-cover", // 主封面
        "fp-title", // 标题
        "fp-subtitle", // 歌手 + 专辑
        "fp-progress", // 进度条
        "controls-row", // 播放操控按钮组
    )

    @Test
    fun defaultFullPlayerSlots_仅含fullPlayer插槽() {
        val slots = ComponentLayout.defaultFullPlayerSlots
        assertEquals("全屏播放器只应有一个 full-player 插槽", setOf("full-player"), slots.keys)
    }

    @Test
    fun fullPlayer插槽_顶层仅一个fpBackdrop背景层容器() {
        val main = ComponentLayout.defaultFullPlayerSlots.getValue("full-player")
        assertEquals("背景层容器应为唯一顶层组件", 1, main.size)
        val backdrop = main[0]
        assertEquals("fp-backdrop", backdrop.id)
        assertTrue(
            "fp-backdrop 必须携带 children 前景层，否则背景层无法叠加内容",
            backdrop.extra["children"] is Map<*, *>,
        )
    }

    @Test
    fun fpBackdrop_children前景组件顺序正确() {
        val main = ComponentLayout.defaultFullPlayerSlots.getValue("full-player")
        @Suppress("UNCHECKED_CAST")
        val children = main[0].extra["children"] as Map<String, List<ComponentEntry>>
        assertEquals("children 只应包含一个前景子插槽", setOf("fp-backdrop"), children.keys)
        val content = children.getValue("fp-backdrop").map { it.id }
        assertEquals(
            listOf(
                "fp-title", // 标题（左上角）
                "fp-subtitle", // 歌手 + 专辑（左上角）
                "fp-cover", // 主封面
                "fp-progress", // 进度条
                "controls-row", // 播放操控按钮组
            ),
            content,
        )
    }

    @Test
    fun 标题与歌手专辑位于前景层顶部_即左上角() {
        val main = ComponentLayout.defaultFullPlayerSlots.getValue("full-player")
        @Suppress("UNCHECKED_CAST")
        val content = (main[0].extra["children"] as Map<String, List<ComponentEntry>>)
            .getValue("fp-backdrop").map { it.id }
        // 标题与副标题必须排在最前，才能渲染在内容区顶部（Text 默认左对齐 → 左上角）
        assertEquals("fp-title", content[0])
        assertEquals("fp-subtitle", content[1])
    }

    @Test
    fun 默认组装引用的所有组件id都在预期注册清单内() {
        val ids = collectAllComponentIds(ComponentLayout.defaultFullPlayerSlots)
        assertEquals(expectedComponentIds, ids)
    }

    @Test
    fun 旧版平铺配置的聚合组件仍保留兼容() {
        // 兼容性：聚合组件（旧配置使用的 id）仍在默认注册组件清单之外，不应出现在新默认组装中
        val ids = collectAllComponentIds(ComponentLayout.defaultFullPlayerSlots)
        assertTrue("新版默认组装不应再引用聚合组件 track-info", "track-info" !in ids)
        assertTrue("新版默认组装不应再引用聚合组件 progress-bar", "progress-bar" !in ids)
    }

    /** 递归收集 slots 中引用的全部组件 id（含 children 嵌套） */
    private fun collectAllComponentIds(slots: Map<String, List<ComponentEntry>>): Set<String> {
        val result = mutableSetOf<String>()
        fun walk(entries: List<ComponentEntry>) {
            for (e in entries) {
                result += e.id
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
