package com.winter.muplayer.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CSS 解析的级联语义 —— 保护以下行为不被回退：
 * - 同名选择器多次出现时**合并属性**（后者覆盖同名属性），而不是整块覆盖；
 *   曾经的整块覆盖会让用户写第二个 `.app-top { }` 时第一块的属性静默丢失
 *   （默认模板的入场动画就踩过这个坑，顶部栏的 `arrange: row; weight: 0` 被吃掉）。
 * - 逗号分组 `.a, .b { }` 应展开为多条规则。
 * - `@keyframes` 块与普通规则分离，不互相污染。
 */
class CssParserMergeTest {

    @Test
    fun 同名选择器属性合并而非覆盖() {
        val rules = CssParser.parse(
            """
            .app-top { arrange: row; weight: 0; }
            .app-top { enter: fade-up 320ms ease-out; }
            """.trimIndent()
        )
        val props = rules[".app-top"]
        assertEquals("row", props?.get("arrange"))
        assertEquals("0", props?.get("weight"))
        assertEquals("fade-up 320ms ease-out", props?.get("enter"))
    }

    @Test
    fun 同名属性后者覆盖前者() {
        val rules = CssParser.parse(".a { color: red; } .a { color: blue; }")
        assertEquals("blue", rules[".a"]?.get("color"))
    }

    @Test
    fun 逗号分组展开为多条规则且属性一致() {
        val rules = CssParser.parse(".a, .b { weight: 0; }")
        assertEquals("0", rules[".a"]?.get("weight"))
        assertEquals("0", rules[".b"]?.get("weight"))
    }

    @Test
    fun 关键帧定义不进入规则表() {
        val result = CssParser.parseAll(
            """
            .app-top { arrange: row; }
            @keyframes fade-up {
              from { opacity: 0; translate-y: 16px; }
              to   { opacity: 1; translate-y: 0; }
            }
            """.trimIndent()
        )
        assertEquals("row", result.rules[".app-top"]?.get("arrange"))
        // 关键帧名不应被当成选择器规则
        assertNull(result.rules["from"])
        assertNull(result.rules[".fade-up"])
        assertEquals(2, result.keyframes["fade-up"]?.frames?.size)
    }

    @Test
    fun 关键帧支持3D旋转通道() {
        val result = CssParser.parseAll(
            """
            @keyframes flip {
              from { opacity: 0; rotate-y: -90deg; }
              to   { opacity: 1; rotate-y: 0; rotate-x: 5deg; }
            }
            """.trimIndent()
        )
        val frames = result.keyframes["flip"]?.frames.orEmpty()
        assertEquals(2, frames.size)
        assertEquals(-90f, frames[0].rotateY ?: 0f, 0.001f)
        assertEquals(0f, frames[1].rotateY ?: -1f, 0.001f)
        assertEquals(5f, frames[1].rotateX ?: 0f, 0.001f)
    }
}
