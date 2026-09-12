package com.winter.muplayer.config

/** CSS 解析结果：样式规则 + 关键帧定义 */
data class CssParseResult(
    val rules: Map<String, Map<String, String>>,
    val keyframes: Map<String, CssKeyframes>,
)

/**
 * 轻量 CSS 解析器 —— 支持 .class 和 #id 选择器，以及 @keyframes 关键帧动画。
 *
 * 支持格式：
 *   .class-name { property: value; }
 *   #component-id { property: value; }
 *   .a, .b { property: value; }          ← 逗号分组（展开为多条规则）
 *   @keyframes name { from { ... } to { ... } }
 *
 * key 规则：
 *   .slot-name { }  → key ".slot-name"  （slot 样式）
 *   #component { }  → key "#component"  （组件样式）
 *
 * 不支持：伪类、组合器、@media 等 @规则（关键帧除外）、多级选择器。
 * 无法识别的选择器被跳过（不报错）。
 */
object CssParser {

    // 通用块：选择器部分 { 属性部分 }
    private val RULE_REGEX = Regex("""([^{}]+)\{([^{}]*)\}""")

    // 合法选择器：.name 或 #name（kebab-case / 下划线 / 数字）
    private val SELECTOR_REGEX = Regex("""^[.#][\w-]+$""")

    /** 解析 CSS 文本，返回 selector → 属性字典（保留 . / # 前缀） */
    fun parse(cssText: String): Map<String, Map<String, String>> = parseAll(cssText).rules

    /**
     * 解析 CSS 文本，同时提取 `@keyframes` 定义。
     *
     * 关键帧块先被剥离再解析规则：否则 `@keyframes` 内部的 `from { ... }`
     * 会被当成普通规则块误解析。
     */
    fun parseAll(cssText: String): CssParseResult {
        if (cssText.isBlank()) return CssParseResult(emptyMap(), emptyMap())
        val cleaned = removeComments(cssText)
        if (cleaned.isBlank()) return CssParseResult(emptyMap(), emptyMap())

        val keyframes = parseKeyframesBlocks(cleaned)
        val rulesText = stripKeyframesBlocks(cleaned)

        val rules = mutableMapOf<String, Map<String, String>>()
        for (match in RULE_REGEX.findAll(rulesText)) {
            val selectorGroup = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            if (selectorGroup.isEmpty() || body.isEmpty()) continue
            if (selectorGroup.startsWith("@")) continue

            val props = parseProperties(body)
            if (props.isEmpty()) continue

            // 逗号分组：.a, .b { ... } → 每个选择器各自持有同一份属性
            // 同名选择器多次出现 → 合并属性（后者覆盖同名属性），符合 CSS 级联语义：
            // 早前是整块覆盖，导致用户写第二个 `.app-top { }` 时第一块的属性静默丢失。
            for (selector in selectorGroup.split(',')) {
                val key = selector.trim()
                if (!SELECTOR_REGEX.matches(key)) continue
                val existing = rules[key]
                rules[key] = if (existing.isNullOrEmpty()) props else existing + props
            }
        }

        return CssParseResult(rules, keyframes)
    }

    /** 从 CSS 块内解析属性 key: value */
    private fun parseProperties(body: String): Map<String, String> {
        val props = mutableMapOf<String, String>()
        // 按 ; 分割，然后解析每个 key: value
        val parts = body.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts) {
            val colonIdx = part.indexOf(':')
            if (colonIdx > 0) {
                val key = part.substring(0, colonIdx).trim()
                val value = part.substring(colonIdx + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    props[key] = value
                }
            }
        }
        return props
    }

    /** 剔除 /* */ 注释 */
    private fun removeComments(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '*') {
                i += 2
                while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                i += 2
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

    /** 移除所有 `@keyframes name { ... }` 块（支持嵌套花括号），保留其余文本 */
    private fun stripKeyframesBlocks(text: String): String {
        val header = Regex("""@keyframes\s+[\w-]+\s*\{""", RegexOption.IGNORE_CASE)
        var result = text
        var searchFrom = 0
        while (true) {
            val match = header.find(result, searchFrom) ?: break
            val bodyEnd = findMatchingBrace(result, match.range.last)
            if (bodyEnd < 0) break
            result = result.substring(0, match.range.first) + result.substring(bodyEnd + 1)
            searchFrom = match.range.first
        }
        return result
    }
}
