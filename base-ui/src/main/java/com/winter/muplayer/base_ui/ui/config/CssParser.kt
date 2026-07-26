package com.winter.muplayer.base_ui.ui.config

/**
 * 轻量 CSS 解析器 —— 支持 .class 和 #id 选择器。
 *
 * 支持格式：
 *   .class-name {
 *     property: value;
 *   }
 *   #component-id {
 *     property: value;
 *   }
 *
 * key 规则：
 *   .slot-name { }  → key ".slot-name"  （slot 样式）
 *   #component { }  → key "#component"  （组件样式）
 *
 * 不支持：伪类、组合器、@规则、多级选择器。
 */
object CssParser {

    /** 解析 CSS 文本，返回 selector → 属性字典（保留 . / # 前缀） */
    fun parse(cssText: String): Map<String, Map<String, String>> {
        val cleaned = removeComments(cssText)
        val rules = mutableMapOf<String, Map<String, String>>()

        // 匹配 .name { } 或 #name { }，保留前缀作为 key
        val blockRegex = Regex("""([.#])([\w-]+)\s*\{([^}]*)\}""")
        for (match in blockRegex.findAll(cleaned)) {
            val prefix = match.groupValues[1]    // "." 或 "#"
            val name = match.groupValues[2]      // "app-top" 或 "app-name"
            val body = match.groupValues[3].trim()
            val key = prefix + name               // ".app-top" 或 "#app-name"
            val props = parseProperties(body)
            if (props.isNotEmpty()) {
                rules[key] = props
            }
        }

        return rules
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
}
