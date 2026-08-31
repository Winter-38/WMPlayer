package com.winter.muplayer.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 嵌套子 slot 路径工具测试 —— 布局编辑器「子 slot 内的子 slot」定位与替换。
 */
class NestedLayoutTest {

    private fun parse(text: String): JSONObject =
        JSONObject(LayoutParser.removeComments(text))

    /** 三层嵌套结构：app-center → level1 → level2（level2 里又有容器 level3）。 */
    private fun layout(): ComponentLayout = StyleConfigLoader.parseConfigObjectStatic(parse("""
        {
          "main": {
            "app-center": [
              "tab-bar",
              { "name": "level1", "children": [
                  "a",
                  { "name": "level2", "children": [
                      "x",
                      { "name": "level3", "children": ["m", "n"] }
                  ]}
              ]},
              "playlist"
            ]
          }
        }
        """.trimIndent()))

    private fun root(l: ComponentLayout): List<ComponentEntry> =
        l.slots.getValue("app-center")

    @Test
    fun 空路径_定位到根列表() {
        assertEquals(
            listOf("tab-bar", LayoutParser.ANONYMOUS_CONTAINER, "playlist"),
            nestedListOf(root(layout()), emptyList()).map { it.id },
        )
    }

    @Test
    fun 一层路径_定位到level1列表() {
        val path = listOf(NestedHop(1, "level1"))
        val list = nestedListOf(root(layout()), path)
        assertEquals(listOf("a", LayoutParser.ANONYMOUS_CONTAINER), list.map { it.id })
    }

    @Test
    fun 两层路径_定位到level2列表() {
        val path = listOf(NestedHop(1, "level1"), NestedHop(1, "level2"))
        assertEquals(
            listOf("x", LayoutParser.ANONYMOUS_CONTAINER),
            nestedListOf(root(layout()), path).map { it.id },
        )
    }

    @Test
    fun 三层路径_定位到level3列表() {
        val path = listOf(
            NestedHop(1, "level1"),
            NestedHop(1, "level2"),
            NestedHop(1, "level3"),
        )
        assertEquals(
            listOf("m", "n"),
            nestedListOf(root(layout()), path).map { it.id },
        )
    }

    @Test
    fun 非法路径_返回空或null() {
        assertTrue(nestedListOf(root(layout()), listOf(NestedHop(9, "x"))).isEmpty())
        assertTrue(nestedListOf(root(layout()), listOf(NestedHop(1, "不存在的子slot"))).isEmpty())
        assertNull(replaceNestedRoot(root(layout()), listOf(NestedHop(9, "x")), emptyList()))
    }

    @Test
    fun 空路径_替换根列表() {
        val newRoot = replaceNestedRoot(root(layout()), emptyList(), listOf(ComponentEntry("playbar")))
        assertNotNull(newRoot)
        assertEquals(listOf("playbar"), newRoot!!.map { it.id })
    }

    @Test
    fun 三层路径_替换level3列表并保持其余结构() {
        val l = layout()
        val path = listOf(
            NestedHop(1, "level1"),
            NestedHop(1, "level2"),
            NestedHop(1, "level3"),
        )
        // 在 level3 中追加一个组件（编辑器「添加组件」的模型操作）
        val current = nestedListOf(root(l), path)
        val newList = current + ComponentEntry("o")
        val newRoot = replaceNestedRoot(root(l), path, newList)
        assertNotNull(newRoot)

        // 重建布局并验证三层结构完整、level3 已更新
        val updated = l.copy(slots = l.slots + ("app-center" to newRoot!!))
        @Suppress("UNCHECKED_CAST")
        val level3 = (updated.slots.getValue("app-center")[1].extra["children"]
            as Map<String, List<ComponentEntry>>).getValue("level1")[1].extra["children"]
            as Map<String, List<ComponentEntry>>
        @Suppress("UNCHECKED_CAST")
        val level3List = level3.getValue("level2")[1].extra["children"]
            as Map<String, List<ComponentEntry>>
        assertEquals(
            listOf("m", "n", "o"),
            level3List.getValue("level3").map { it.id },
        )
        // 顶层普通组件不受影响
        assertEquals("tab-bar", updated.slots.getValue("app-center")[0].id)
        assertEquals("playlist", updated.slots.getValue("app-center")[2].id)

        // 序列化往返保持
        val roundTrip = StyleConfigLoader.parseConfigObjectStatic(
            parse(ConfigWriter.layoutToJson(updated)),
        )
        @Suppress("UNCHECKED_CAST")
        val rtLevel3 = (roundTrip.slots.getValue("app-center")[1].extra["children"]
            as Map<String, List<ComponentEntry>>).getValue("level1")[1].extra["children"]
            as Map<String, List<ComponentEntry>>
        @Suppress("UNCHECKED_CAST")
        val rtLevel3List = rtLevel3.getValue("level2")[1].extra["children"]
            as Map<String, List<ComponentEntry>>
        assertEquals(listOf("m", "n", "o"), rtLevel3List.getValue("level3").map { it.id })
    }
}
